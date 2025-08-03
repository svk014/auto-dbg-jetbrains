package com.github.svk014.autodbgjetbrains.debugger.java

sealed class VariableExtractionError : Exception() {
    data class TimeoutError(override val message: String) : VariableExtractionError()
    data class DebuggerNotAvailable(override val message: String) : VariableExtractionError()
    data class InvalidFrame(override val message: String) : VariableExtractionError()
    data class ValueComputationError(override val message: String) : VariableExtractionError()
}

