package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.VariableRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.ObjectSummary
import com.github.svk014.autodbgjetbrains.debugger.models.SerializedVariable
import com.github.svk014.autodbgjetbrains.debugger.utils.AsyncDebuggerUtils
import com.intellij.debugger.engine.JavaValue
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame

class JavaVariableRetriever(private val project: Project) : VariableRetriever {

    companion object {
        private const val FRAME_FETCH_TIMEOUT_SECONDS = 5L
    }

    override fun getFrameVariables(frameId: String, maxDepth: Int): Map<String, SerializedVariable> {
        return try {
            val debuggerManager = XDebuggerManager.getInstance(project)
            val currentSession = debuggerManager.currentSession ?: return emptyMap()

            if (!currentSession.isSuspended) return emptyMap()

            val suspendContext = currentSession.suspendContext ?: return emptyMap()
            val activeExecutionStack = suspendContext.activeExecutionStack ?: return emptyMap()

            val depth = frameId.toIntOrNull() ?: 0

            val framesFuture = AsyncDebuggerUtils.fetchStackFramesAsync(activeExecutionStack)
            val frames = AsyncDebuggerUtils.safeGet(framesFuture, FRAME_FETCH_TIMEOUT_SECONDS, emptyList())

            if (depth >= frames.size) return emptyMap()

            val targetFrame = frames[depth]
            extractVariablesFromFrame(targetFrame)
        } catch (ex: Exception) {
            thisLogger().error("Error getting frame variables for frameId: $frameId", ex)
            emptyMap()
        }
    }

    private fun extractVariablesFromFrame(frame: XStackFrame): Map<String, SerializedVariable> {
        return try {
            val variablesFuture = AsyncDebuggerUtils.fetchFrameVariablesAsync(frame)
            val rawVariables = AsyncDebuggerUtils.safeGet(variablesFuture, FRAME_FETCH_TIMEOUT_SECONDS, emptyList())

            val javaValues = rawVariables.map { it.second as JavaValue }
            val serializedList = SmartSerializer.serializeVariables(javaValues)

            serializedList.associateBy { it.name }
        } catch (ex: Exception) {
            thisLogger().error("Error extracting variables from frame", ex)
            mapOf(
                "error" to SerializedVariable(
                    "error",
                    ObjectSummary("Error", "Failed to extract variables: ${ex.message}")
                )
            )
        }
    }
}