package com.github.svk014.autodbgjetbrains.server.models

import com.intellij.xdebugger.breakpoints.XBreakpoint

/**
 * Events that can trigger state transitions
 */
sealed class DebuggerEvent {
    data class BreakpointHit(val location: String, val frameInfo: Any, val breakpoint: XBreakpoint<*>? = null) :
        DebuggerEvent()

    data class StepCompleted(val location: String) : DebuggerEvent()
    data class FunctionEntered(val functionName: String, val args: Map<String, Any>) : DebuggerEvent()
    data class FunctionExited(val functionName: String, val returnValue: Any?) : DebuggerEvent()
    data class VariableChanged(val name: String, val oldValue: Any?, val newValue: Any?) : DebuggerEvent()
    object ExecutionContinued : DebuggerEvent()
    data class OperationCompleted(val operationId: String) : DebuggerEvent()
    data class ErrorOccurred(val error: String) : DebuggerEvent()
}
