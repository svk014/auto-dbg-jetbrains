package com.github.svk014.autodbgjetbrains.debugger.interfaces

import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.github.svk014.autodbgjetbrains.debugger.models.SerializedVariable
import com.github.svk014.autodbgjetbrains.models.SourceLine

interface FrameRetriever {
    fun getFrameAt(depth: Int): FrameInfo?
}

interface CallStackRetriever {
    fun getCallStack(maxDepth: Int = 10): List<FrameInfo>
}

interface VariableRetriever {
    fun getFrameVariables(frameId: String, maxDepth: Int = 3): Map<String, SerializedVariable>
}

interface BreakpointType

interface ExecutionController {
    fun stepOver()
    fun stepInto()
    fun continueExecution()
    fun setBreakpoint(file: String, line: SourceLine, condition: String? = null, type: BreakpointType? = null)
    fun stepOut()
}
