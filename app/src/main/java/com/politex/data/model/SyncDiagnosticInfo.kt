package com.politex.data.model

data class SyncDiagnosticInfo(
    val lastSyncTime: String = "Не выполнялась",
    val checkedUrl: String = "https://guo-mpk.by/raspisanie/",
    val httpStatusCode: Int = 0,
    val receivedBytes: Long = 0,
    val lessonsFound: Int = 0,
    val statusMessage: String = "Ожидание синхронизации",
    val isSuccess: Boolean = false,
    val isSyncing: Boolean = false,
    val responsePreview: String = "",
    val parseFormat: String = "",
    val parseStrategy: String = "",
    val parseRuns: Int = 0,
    val groupFound: Boolean = false,
    val parseError: String = "",
    val textPreview: String = "",
)
