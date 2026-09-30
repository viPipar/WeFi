package com.wefi.analyzer.data.repository

import com.wefi.analyzer.domain.model.SpeedTestStage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class SpeedTestRepositoryImplTest {

    @Test
    fun runSpeedTest_whenOfflineWithUnknownHost_emitsOfflineLabModeGracefully() = runBlocking {
        // OkHttpClient dengan interceptor yang selalu melempar UnknownHostException (ketiadaan DNS/WAN)
        val offlineClient = OkHttpClient.Builder()
            .addInterceptor {
                throw UnknownHostException("No address associated with hostname")
            }
            .build()

        val repo = SpeedTestRepositoryImpl(offlineClient)
        val metricsList = repo.runSpeedTest().toList()

        val lastMetric = metricsList.last()
        assertEquals(SpeedTestStage.OFFLINE_LAB_MODE, lastMetric.stage)
        assertFalse(lastMetric.isRunning)
        assertEquals(1.0f, lastMetric.progress)
        assertTrue("Error message harus menjelaskan mode lab offline", 
            lastMetric.errorMessage?.contains("Offline") == true || lastMetric.errorMessage?.contains("Lab") == true
        )
    }
}
