package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame

/**
 * Java-specific implementation for retrieving stack frame information
 */
class JavaFrameRetriever(private val project: Project) : FrameRetriever {

    override fun getFrameAt(depth: Int): FrameInfo? {
        return try {
            val debuggerManager = XDebuggerManager.getInstance(project)
            val currentSession = debuggerManager.currentSession ?: return null

            if (!currentSession.isSuspended) return null

            val suspendContext = currentSession.suspendContext ?: return null
            val activeExecutionStack = suspendContext.activeExecutionStack ?: return null

            // Get the stack frames
            val frames = mutableListOf<XStackFrame>()
            activeExecutionStack.computeStackFrames(0, object : com.intellij.xdebugger.frame.XExecutionStack.XStackFrameContainer {
                override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
                    frames.addAll(stackFrames)
                }

                override fun errorOccurred(errorMessage: String) {
                    // Handle error - frames will remain empty
                }
            })

            // Return the frame at the specified depth
            if (depth < frames.size) {
                val frame = frames[depth]
                val sourcePosition = frame.sourcePosition

                FrameInfo(
                    methodName = extractMethodName(frame),
                    lineNumber = sourcePosition?.line ?: -1,
                    filePath = sourcePosition?.file?.path ?: "Unknown"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            // Log error if needed and return null
            null
        }
    }

    /**
     * Extract method name from stack frame
     */
    private fun extractMethodName(frame: XStackFrame): String {
        return try {
            // Try to get method name from the frame's string representation
            val frameString = frame.toString()

            // Parse method name from frame string (format may vary)
            when {
                frameString.contains("at ") -> {
                    val methodPart = frameString.substringAfter("at ").substringBefore("(")
                    methodPart.substringAfterLast(".")
                }
                frameString.contains("::") -> {
                    frameString.substringAfter("::").substringBefore("(")
                }
                else -> "Unknown Method"
            }
        } catch (e: Exception) {
            "Unknown Method"
        }
    }
}
