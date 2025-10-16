package com.github.svk014.autodbgjetbrains.models

import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import kotlinx.serialization.Serializable

@Serializable
enum class DebuggerStatus {
    NOT_CONNECTED,
    RUNNING,
    PAUSED,
    STOPPED,
    UNKNOWN
}

@Serializable
data class DebuggerState(
    val status: DebuggerStatus,
    val sessionName: String? = null,
    val currentPosition: FrameInfo? = null,
    val isConnected: Boolean = false,
    val availableSessions: List<String> = emptyList()
)
