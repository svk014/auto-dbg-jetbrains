package com.github.svk014.autodbgjetbrains.debugger

import com.github.svk014.autodbgjetbrains.debugger.factory.DebuggerComponentFactory
import com.github.svk014.autodbgjetbrains.debugger.interfaces.CallStackRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.VariableRetriever
import com.github.svk014.autodbgjetbrains.debugger.java.JavaExecutionController
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.github.svk014.autodbgjetbrains.debugger.models.SerializedVariable
import com.github.svk014.autodbgjetbrains.models.BreakpointType
import com.github.svk014.autodbgjetbrains.models.SerializableBreakpoint
import com.github.svk014.autodbgjetbrains.models.SourceLine
import com.github.svk014.autodbgjetbrains.toolWindow.MyToolWindowFactory.MyToolWindow.Companion.appendLog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Service(Service.Level.PROJECT)
class DebuggerIntegrationService(private val project: Project) {

    // Store active sessions, keyed by a unique name. This map is now populated manually.
    private val activeSessions = mutableMapOf<String, XDebugSession>()

    // Language-specific component implementations for the *selected* session
    private var frameRetriever: FrameRetriever? = null
    private var callStackRetriever: CallStackRetriever? = null
    private var variableRetriever: VariableRetriever? = null
    private var selectedSessionName: String? = null


    private var executionController: JavaExecutionController = JavaExecutionController(project)

    /**
     * Manually queries the IDE for all active debug sessions, updates the internal map,
     * and returns a list of their names. The UI should call this on "Refresh".
     */
    fun refreshAndGetActiveSessionNames(): List<String> {
        thisLogger().info("Refreshing active debug sessions...")
        activeSessions.clear()
        XDebuggerManager.getInstance(project).debugSessions.forEach { session ->
            if (!session.isStopped) {
                activeSessions[session.sessionName] = session
            }
        }
        val sessionNames = activeSessions.keys.toList()
        thisLogger().info("Found active sessions: $sessionNames")
        appendLog("Found active sessions: $sessionNames")
        return sessionNames
    }

    fun getDebugSessions(): Array<XDebugSession> {
        return XDebuggerManager.getInstance(project).debugSessions
    }

    fun connectToCurrentSession() {
        val currentSession = XDebuggerManager.getInstance(project).currentSession
        println(currentSession)
        if (currentSession != null && !currentSession.isStopped) {
            connectToSession(currentSession.sessionName)
        } else {
            thisLogger().warn("No current active debug session to connect to.")
            appendLog("Error: No current active debug session found.")
            clearComponents()
        }
    }


    /**
     * Connects to a specific debug session by its name and initializes the appropriate retrievers.
     * This should be called when the user selects a session from the tool window dropdown and clicks "Connect".
     */
    fun connectToSession(sessionName: String) {
        val session = activeSessions[sessionName]
        if (session == null) {
            thisLogger().warn("Attempted to connect to a non-existent or stopped session: $sessionName")
            appendLog("Error: Session '$sessionName' not found.")
            clearComponents()
            return
        }

        // Acknowledge connection
        selectedSessionName = sessionName
        thisLogger().info("Connecting to debug session: $sessionName")
        appendLog("Connecting to debug session: $sessionName")

        // Initialize components based on the selected session's language
        initializeComponents(session)
    }

    /**
     * Initializes the language-specific components based on the selected debug session.
     */
    private fun initializeComponents(session: XDebugSession) {
        val processClass = session.debugProcess.javaClass.name
        val detectedLanguage = DebuggerComponentFactory.detectLanguage(processClass)

        frameRetriever = DebuggerComponentFactory.createFrameRetriever(detectedLanguage, project)
        callStackRetriever = DebuggerComponentFactory.createCallStackRetriever(detectedLanguage, project)
        variableRetriever = DebuggerComponentFactory.createVariableRetriever(detectedLanguage, project)

        thisLogger().info("Initialized components for $detectedLanguage debugger.")
        appendLog("Successfully connected to $selectedSessionName ($detectedLanguage). Ready for API calls.")
    }

    /**
     * Clears all active components and disconnects from the session.
     */
    private fun clearComponents() {
        frameRetriever = null
        callStackRetriever = null
        variableRetriever = null
        val previouslySelected = selectedSessionName
        selectedSessionName = null
        if (previouslySelected != null) {
            thisLogger().info("Disconnected from session: $previouslySelected")
            appendLog("Disconnected from session: $previouslySelected")
        }
    }

    fun getFrameAt(depth: Int): FrameInfo? {
        return frameRetriever?.getFrameAt(depth)
    }

    fun getCallStack(maxDepth: Int = 10): List<FrameInfo> {
        return callStackRetriever?.getCallStack(maxDepth) ?: emptyList()
    }

    fun getFrameVariables(frameId: String, maxDepth: Int = 3): Map<String, SerializedVariable> {
        return variableRetriever?.getFrameVariables(frameId, maxDepth) ?: emptyMap()
    }

    fun getCurrentSession(): XDebugSession? {
        return selectedSessionName?.let { activeSessions[it] }
    }

    /**
     * Pauses the currently connected debug session.
     */
    fun pauseCurrentSession() {
        val session = getCurrentSession()
        if (session != null) {
            if (!session.isStopped && !session.isPaused) {
                session.pause()
                appendLog("Sent pause command to: ${session.sessionName}")
            } else {
                appendLog("Session '${session.sessionName}' is already paused or not in a state to be paused.")
            }
        } else {
            appendLog("Error: No session is currently connected to pause.")
        }
    }

    // --- Step control methods ---
    suspend fun stepOver(): Boolean = suspendCancellableCoroutine { continuation ->
        ApplicationManager.getApplication().invokeLater {
            try {
                executionController.stepOver()
                continuation.resume(true)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun stepInto(): Boolean = suspendCancellableCoroutine { continuation ->
        ApplicationManager.getApplication().invokeLater {
            try {
                executionController.stepInto()
                continuation.resume(true)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun stepOut(): Boolean = suspendCancellableCoroutine { continuation ->
        ApplicationManager.getApplication().invokeLater {
            try {
                executionController.stepOut()
                continuation.resume(true)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun setBreakpoint(
        file: String, line: SourceLine, condition: String?, type: BreakpointType?, lambdaOrdinal: Int?,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        ApplicationManager.getApplication().invokeLater {
            try {
                val result = executionController.setBreakpoint(
                    file = file,
                    line = line,
                    condition = condition,
                    type = type,
                    lambdaOrdinal = lambdaOrdinal,
                )
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun removeBreakpoint(
        file: String, line: SourceLine, condition: String?, type: BreakpointType?, lambdaOrdinal: Int?,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        ApplicationManager.getApplication().invokeLater {
            try {
                val result = executionController.removeBreakpoint(
                    file = file,
                    line = line,
                    condition = condition,
                    type = type,
                    lambdaOrdinal = lambdaOrdinal,
                )
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun getAllBreakpoints(): List<SerializableBreakpoint> = suspendCancellableCoroutine { continuation ->
        ApplicationManager.getApplication().invokeLater {
            try {
                val result = executionController.getAllBreakpoints()
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun continueExecution(): Boolean = suspendCancellableCoroutine { continuation ->
        ApplicationManager.getApplication().invokeLater {
            try {
                executionController.continueExecution()
                continuation.resume(true)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    fun getCurrentDebuggerState(): com.github.svk014.autodbgjetbrains.models.DebuggerState {
        val availableSessions = refreshAndGetActiveSessionNames()
        val currentSession = getCurrentSession()

        return when {
            currentSession == null -> {
                com.github.svk014.autodbgjetbrains.models.DebuggerState(
                    status = com.github.svk014.autodbgjetbrains.models.DebuggerStatus.NOT_CONNECTED,
                    isConnected = false,
                    availableSessions = availableSessions
                )
            }

            currentSession.isStopped -> {
                com.github.svk014.autodbgjetbrains.models.DebuggerState(
                    status = com.github.svk014.autodbgjetbrains.models.DebuggerStatus.STOPPED,
                    sessionName = selectedSessionName,
                    isConnected = true,
                    availableSessions = availableSessions
                )
            }

            currentSession.isPaused -> {
                com.github.svk014.autodbgjetbrains.models.DebuggerState(
                    status = com.github.svk014.autodbgjetbrains.models.DebuggerStatus.PAUSED,
                    sessionName = selectedSessionName,
                    currentPosition = getFrameAt(0),
                    isConnected = true,
                    availableSessions = availableSessions
                )
            }

            else -> {
                com.github.svk014.autodbgjetbrains.models.DebuggerState(
                    status = com.github.svk014.autodbgjetbrains.models.DebuggerStatus.RUNNING,
                    sessionName = selectedSessionName,
                    isConnected = true,
                    availableSessions = availableSessions
                )
            }
        }
    }
}
