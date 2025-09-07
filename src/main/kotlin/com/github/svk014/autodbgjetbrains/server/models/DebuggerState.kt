package com.github.svk014.autodbgjetbrains.server.models

enum class DebuggerState {
    IDLE,
    TRACING_FUNCTION,
    FINDING_VALUE_CHANGE,
    PAUSED,
    STEPPING_THROUGH,
    WAITING_FOR_CONDITION
}
