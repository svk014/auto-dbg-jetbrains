package com.github.svk014.autodbgjetbrains.debugger.interfaces

import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.github.svk014.autodbgjetbrains.debugger.models.Variable

interface FrameRetriever {
    fun getFrameAt(depth: Int): FrameInfo?
}

interface CallStackRetriever {
    fun getCallStack(maxDepth: Int = 10): List<FrameInfo>
}

interface VariableRetriever {
    fun getFrameVariables(frameId: String, maxDepth: Int = 3): Map<String, Variable>
}
