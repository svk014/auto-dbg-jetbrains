package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack.XStackFrameContainer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class JavaFrameRetriever(private val project: Project) : FrameRetriever {

    override fun getFrameAt(depth: Int): FrameInfo? {
        return try {
            val session = getActiveDebugSession() ?: return null
            val frames = fetchStackFrames(session)
            extractFrameInfo(frames, depth)
        } catch (e: Exception) {
            thisLogger().error("Error getting frame at depth $depth", e)
            null
        }
    }

    private fun getActiveDebugSession(): XDebugSession? {
        val debuggerManager = XDebuggerManager.getInstance(project)
        val currentSession = debuggerManager.currentSession ?: return null

        if (!currentSession.isSuspended) return null

        return currentSession
    }

    private fun fetchStackFrames(session: XDebugSession): List<JavaStackFrame> {
        val suspendContext = session.suspendContext ?: throw IllegalStateException("No suspend context available")

        val activeExecutionStack =
            suspendContext.activeExecutionStack ?: throw IllegalStateException("No active execution stack available")

        val framesFuture = CompletableFuture<List<JavaStackFrame>>()
        val allFrames = mutableListOf<JavaStackFrame>()

        activeExecutionStack.computeStackFrames(0, createStackFrameContainer(framesFuture, allFrames))

        return framesFuture.get(5, TimeUnit.SECONDS)
    }

    private fun createStackFrameContainer(
        framesFuture: CompletableFuture<List<JavaStackFrame>>, allFrames: MutableList<JavaStackFrame>
    ) = object : XStackFrameContainer {

        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
            try {
                val javaFrames = stackFrames.filterIsInstance<JavaStackFrame>()
                allFrames.addAll(javaFrames)
                if (last) {
                    framesFuture.complete(allFrames.toList())
                }
            } catch (e: Exception) {
                framesFuture.completeExceptionally(e)
            }
        }

        override fun errorOccurred(errorMessage: String) {
            framesFuture.completeExceptionally(
                RuntimeException("Stack frame computation failed: $errorMessage")
            )
        }
    }

    private fun extractFrameInfo(frames: List<JavaStackFrame>, depth: Int): FrameInfo? {
        if (depth >= frames.size) return null

        val frame = frames[depth]
        val sourcePosition = frame.sourcePosition

        return FrameInfo(
            methodName = frame.descriptor.method?.name() ?: "Unknown Method",
            // Convert from 0-based to 1-based line numbering for user display
            lineNumber = sourcePosition.let { if (it != null) it.line + 1 else -1 },
            filePath = sourcePosition?.file?.path ?: "Unknown"
        )
    }
}
