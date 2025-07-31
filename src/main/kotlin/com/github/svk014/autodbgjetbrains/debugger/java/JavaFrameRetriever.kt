package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.debugger.engine.JavaStackFrame

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
                    // Filter for Java frames only
                    val javaFrames = stackFrames.filterIsInstance<JavaStackFrame>()
                    frames.addAll(javaFrames)
                }

                override fun errorOccurred(errorMessage: String) {
                    // Handle error - frames will remain empty
                }
            })

            // Return the Java frame at the specified depth
            if (depth < frames.size) {
                val frame = frames[depth] as JavaStackFrame
                val sourcePosition = frame.sourcePosition

                FrameInfo(
                    methodName = frame.descriptor.method?.name() ?: "Unknown Method",

                    // Convert from 0-based to 1-based line numbering for user display
                    lineNumber = sourcePosition.let { if (it != null) it.line + 1 else -1 },
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
}
