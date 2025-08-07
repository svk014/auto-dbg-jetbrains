package com.github.svk014.autodbgjetbrains.debugger

import com.github.svk014.autodbgjetbrains.debugger.factory.DebuggerComponentFactory
import com.github.svk014.autodbgjetbrains.debugger.interfaces.CallStackRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.VariableRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.github.svk014.autodbgjetbrains.debugger.models.Variable
import com.github.svk014.autodbgjetbrains.toolWindow.MyToolWindowFactory.MyToolWindow.Companion.appendLog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebugSession

@Service(Service.Level.PROJECT)
class DebuggerIntegrationService(private val project: Project) {

    // Store active sessions, keyed by a unique name. This map is now populated manually.
    private val activeSessions = mutableMapOf<String, XDebugSession>()

    // Language-specific component implementations for the *selected* session
    private var frameRetriever: FrameRetriever? = null
    private var callStackRetriever: CallStackRetriever? = null
    private var variableRetriever: VariableRetriever? = null
    private var selectedSessionName: String? = null

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

    fun getFrameVariables(frameId: String, maxDepth: Int = 3): Map<String, Variable> {
        return variableRetriever?.getFrameVariables(frameId, maxDepth) ?: emptyMap()
    }

    private fun getCurrentSession(): XDebugSession? {
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
    fun stepOver(): Boolean {
        val session = getCurrentSession()
        if (session != null) {
            ApplicationManager.getApplication().invokeLater {
                session.stepOver(false)
            }
            return true
        } else {
            appendLog("Error: No session is currently connected for stepping over.")
        }
        return false
    }

    fun stepInto(): Boolean {
        val session = getCurrentSession()
        if (session != null) {
            ApplicationManager.getApplication().invokeLater {
                session.stepInto()
            }
            return true
        } else {
            appendLog("Error: No session is currently connected for stepping in.")
        }
        return false
    }

    fun stepOut(): Boolean {
        val session = getCurrentSession()
        if (session != null) {
            ApplicationManager.getApplication().invokeLater {
                session.stepOut()
            }
            return true
        } else {
            appendLog("Error: No session is currently connected for stepping out.")
        }
        return false
    }

}
