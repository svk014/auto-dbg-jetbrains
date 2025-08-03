package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.github.svk014.autodbgjetbrains.debugger.utils.AsyncDebuggerUtils
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.xdebugger.XDebugSession

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

        val framesFuture = AsyncDebuggerUtils.fetchStackFramesAsync(activeExecutionStack)
        val allFrames = AsyncDebuggerUtils.safeGet(framesFuture, 5, emptyList())

        return allFrames.filterIsInstance<JavaStackFrame>()
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
