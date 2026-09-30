package com.wefi.analyzer.domain.model

sealed interface HybridRouterStatus {
    object Idle : HybridRouterStatus
    data class Testing(val currentPasswordIndex: Int, val totalPasswords: Int) : HybridRouterStatus
    data class Found(val workingPassword: String) : HybridRouterStatus
    data class NotFound(val testedCount: Int) : HybridRouterStatus
    data class VerifiedFromVault(val workingPassword: String) : HybridRouterStatus
}
