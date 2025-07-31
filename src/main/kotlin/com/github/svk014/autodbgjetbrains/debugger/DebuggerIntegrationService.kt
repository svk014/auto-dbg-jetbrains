package com.github.svk014.autodbgjetbrains.debugger

import com.github.svk014.autodbgjetbrains.debugger.factory.DebuggerComponentFactory
import com.github.svk014.autodbgjetbrains.debugger.interfaces.CallStackRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.VariableRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.Variable
import com.github.svk014.autodbgjetbrains.debugger.models.FrameInfo
import com.github.svk014.autodbgjetbrains.toolWindow.MyToolWindowFactory.MyToolWindow.Companion.appendLog
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugSessionListener

@Service(Service.Level.PROJECT)
class DebuggerIntegrationService(private val project: Project) {
    private val connection: MessageBusConnection = project.messageBus.connect()

    // Language-specific component implementations
    private var frameRetriever: FrameRetriever? = null
    private var callStackRetriever: CallStackRetriever? = null
    private var variableRetriever: VariableRetriever? = null
    private var currentLanguage: DebuggerComponentFactory.Language? = null

    init {
        connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
            override fun processStarted(debugProcess: com.intellij.xdebugger.XDebugProcess) {
                val session = debugProcess.session
                val processClass = debugProcess.javaClass.name
                val sessionClass = session.javaClass.name

                // Detect language and initialize appropriate components
                val detectedLanguage = DebuggerComponentFactory.detectLanguage(processClass)
                initializeComponents(detectedLanguage)

                val processType = when (detectedLanguage) {
                    DebuggerComponentFactory.Language.JAVA -> "Java Debugger"
                    DebuggerComponentFactory.Language.JAVASCRIPT -> "JavaScript Debugger"
                    DebuggerComponentFactory.Language.PYTHON -> "Python Debugger"
                    DebuggerComponentFactory.Language.UNKNOWN -> "Unknown Debugger"
                }

                val msg =
                    "[Auto DBG] Debug session started: ${session.sessionName} | Type: $sessionClass | DebugProcess: $processClass | Detected: $processType"
                thisLogger().info(msg)
                appendLog(msg)
                debugProcess.session.addSessionListener(object : XDebugSessionListener {
                    override fun sessionPaused() {
                        thisLogger().info("[Auto DBG] Debug session paused ($processType)")
                        appendLog("[Auto DBG] Debug session paused ($processType)")
                    }

                    override fun sessionResumed() {
                        thisLogger().info("[Auto DBG] Debug session resumed ($processType)")
                        appendLog("[Auto DBG] Debug session resumed ($processType)")
                    }

                    override fun sessionStopped() {
                        thisLogger().info("[Auto DBG] Debug session stopped ($processType)")
                        appendLog("[Auto DBG] Debug session stopped ($processType)")
                        // Clear components when session stops
                        clearComponents()
                    }
                })
            }
        })
        thisLogger().info("[Auto DBG] DebuggerIntegrationService initialized and listening for debug sessions.")
        appendLog("[Auto DBG] DebuggerIntegrationService initialized and listening for debug sessions.")
    }

    private fun initializeComponents(language: DebuggerComponentFactory.Language) {
        if (currentLanguage != language) {
            currentLanguage = language
            frameRetriever = DebuggerComponentFactory.createFrameRetriever(language, project)
            callStackRetriever = DebuggerComponentFactory.createCallStackRetriever(language, project)
            variableRetriever = DebuggerComponentFactory.createVariableRetriever(language, project)

            thisLogger().info("[Auto DBG] Initialized components for language: $language")
            appendLog("[Auto DBG] Initialized components for language: $language")
        }
    }

    /**
     * Clear components when debug session ends
     */
    private fun clearComponents() {
        frameRetriever = null
        callStackRetriever = null
        variableRetriever = null
        currentLanguage = null
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
}
