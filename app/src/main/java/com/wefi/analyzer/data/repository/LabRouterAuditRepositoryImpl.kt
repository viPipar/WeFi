package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.LabAuditLogEntry
import com.wefi.analyzer.domain.model.LabAuditStatus
import com.wefi.analyzer.domain.model.LabAuditTarget
import com.wefi.analyzer.domain.repository.LabRouterAuditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LabRouterAuditRepositoryImpl(
    private val labApiEndpoint: String? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) : LabRouterAuditRepository {

    override val authorizedSsids: Set<String> = emptySet()

    private val _auditLogs = MutableStateFlow<List<LabAuditLogEntry>>(emptyList())
    override val auditLogs: StateFlow<List<LabAuditLogEntry>> = _auditLogs.asStateFlow()

    override fun isSsidAuthorized(ssid: String): Boolean = ssid.isNotBlank()

    override fun testRouterCredential(
        target: LabAuditTarget,
        candidateKey: String
    ): Flow<LabAuditStatus> = flow {
        if (target.ssid.isBlank()) {
            emit(LabAuditStatus.ERROR)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.ERROR,
                    notes = "SSID kosong"
                )
            )
            return@flow
        }

        val candidate = candidateKey.trim()
        if (candidate.isEmpty()) {
            emit(LabAuditStatus.ERROR)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.ERROR,
                    notes = "Kredensial input kosong"
                )
            )
            return@flow
        }

        if (candidate.length > MAX_CANDIDATE_LENGTH) {
            emit(LabAuditStatus.ERROR)
            recordLog(
                LabAuditLogEntry(
                    targetSsid = target.ssid,
                    targetBssid = target.bssid,
                    status = LabAuditStatus.ERROR,
                    notes = "Panjang kredensial melebihi batas"
                )
            )
            return@flow
        }

        emit(LabAuditStatus.TESTING)
        delay(SIMULATED_LATENCY_MS)

        val endpointResult = verifyWithLabEndpoint(target.ssid, candidate)

        val finalStatus = when (endpointResult) {
            VerifyResult.Matched -> LabAuditStatus.MATCHED
            VerifyResult.NotMatched -> LabAuditStatus.FAILED
            VerifyResult.Unavailable -> {
                val local = isCredentialMatched(target.ssid, candidate)
                if (local) LabAuditStatus.MATCHED else LabAuditStatus.FAILED
            }
            VerifyResult.Error -> LabAuditStatus.ERROR
        }

        val notes = when (finalStatus) {
            LabAuditStatus.MATCHED -> "Kredensial cocok (MATCH)"
            LabAuditStatus.FAILED -> "Kredensial tidak cocok (MISMATCH)"
            LabAuditStatus.ERROR -> "Endpoint lab tidak merespons / error"
            else -> "Status tak terduga"
        }

        emit(finalStatus)
        recordLog(
            LabAuditLogEntry(
                targetSsid = target.ssid,
                targetBssid = target.bssid,
                status = finalStatus,
                notes = notes
            )
        )
    }

    private enum class VerifyResult { Matched, NotMatched, Unavailable, Error }

    private suspend fun verifyWithLabEndpoint(
        ssid: String,
        candidate: String
    ): VerifyResult = withContext(Dispatchers.IO) {
        val endpoint = labApiEndpoint ?: return@withContext VerifyResult.Unavailable

        try {
            val jsonBody = JSONObject().apply {
                put("ssid", ssid)
                put("candidate", candidate)
            }.toString()

            val request = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext VerifyResult.Error
                }
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext VerifyResult.Error
                }
                val json = JSONObject(bodyStr)
                if (!json.has("valid")) {
                    return@withContext VerifyResult.Error
                }
                if (json.getBoolean("valid")) {
                    VerifyResult.Matched
                } else {
                    VerifyResult.NotMatched
                }
            }
        } catch (e: Exception) {
            VerifyResult.Unavailable
        }
    }

    private fun isCredentialMatched(ssid: String, candidate: String): Boolean {
        val base = ssid.trim()
        if (base.isEmpty()) return false

        val expected = buildSet {
            add(base)
            add("${base}123")
            add("${base}2026")
            add("lab$base")
            add("${base}_lab")
            add("${base}@lab")
            add("admin$base")
        }

        val candidateHash = sha256(candidate)
        return expected.any { constantTimeEquals(sha256(it), candidateHash) }
    }

    override fun recordLog(entry: LabAuditLogEntry) {
        val current = _auditLogs.value.toMutableList()
        current.add(0, entry)
        _auditLogs.value = if (current.size > MAX_LOG_ENTRIES) {
            current.take(MAX_LOG_ENTRIES)
        } else current
    }

    override fun clearLogs() {
        _auditLogs.value = emptyList()
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 100
        const val MAX_CANDIDATE_LENGTH = 128
        const val SIMULATED_LATENCY_MS = 350L

        fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
            return diff == 0
        }
    }
}