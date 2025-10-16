package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.CallStackRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.github.svk014.autodbgjetbrains.models.SourceLine
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame

/**
 * Java-specific implementation for retrieving call stack information
 */
class JavaCallStackRetriever(private val project: Project) : CallStackRetriever {

    override fun getCallStack(maxDepth: Int): List<FrameInfo> {
        return try {
            val debuggerManager = XDebuggerManager.getInstance(project)
            val currentSession = debuggerManager.currentSession ?: return emptyList()

            if (!currentSession.isSuspended) return emptyList()

            val suspendContext = currentSession.suspendContext ?: return emptyList()
            val activeExecutionStack = suspendContext.activeExecutionStack ?: return emptyList()

            // Get the stack frames
            val frames = mutableListOf<XStackFrame>()
            activeExecutionStack.computeStackFrames(
                0,
                object : com.intellij.xdebugger.frame.XExecutionStack.XStackFrameContainer {
                    override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
                        frames.addAll(stackFrames.take(maxDepth))
                    }

                    override fun errorOccurred(errorMessage: String) {
                        // Handle error - frames will remain empty
                    }
                })

            // Convert XStackFrames to FrameInfo objects
            frames.mapIndexedNotNull { index, frame ->
                if (index >= maxDepth) return@mapIndexedNotNull null

                val sourcePosition = frame.sourcePosition

                FrameInfo(
                    methodName = extractMethodName(frame),
                    lineNumber = SourceLine.zeroToOneBased(sourcePosition?.line),
                    filePath = sourcePosition?.file?.path ?: "Unknown"
                )
            }
        } catch (e: Exception) {
            emptyList()
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

                frameString.contains(".") -> {
                    // Extract method name from class.method format
                    frameString.substringAfterLast(".").substringBefore("(").substringBefore(" ")
                }

                else -> "Unknown Method"
            }
        } catch (e: Exception) {
            "Unknown Method"
        }
    }
}
