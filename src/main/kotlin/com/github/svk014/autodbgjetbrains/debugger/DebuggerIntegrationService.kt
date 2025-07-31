package com.github.svk014.autodbgjetbrains.debugger

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
    init {
        connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
            override fun processStarted(debugProcess: com.intellij.xdebugger.XDebugProcess) {
                val session = debugProcess.session
                val processClass = debugProcess.javaClass.name
                val sessionClass = session.javaClass.name
                val processType = when {
                    processClass.contains("java", ignoreCase = true) -> "Java Debugger"
                    processClass.contains("js", ignoreCase = true) || processClass.contains("javascript", ignoreCase = true) -> "JavaScript Debugger"
                    else -> "Other/Unknown Debugger"
                }
                val msg = "[Auto DBG] Debug session started: ${session.sessionName} | Type: $sessionClass | DebugProcess: $processClass | Detected: $processType"
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
                    }
                })
            }
        })
        thisLogger().info("[Auto DBG] DebuggerIntegrationService initialized and listening for debug sessions.")
        appendLog("[Auto DBG] DebuggerIntegrationService initialized and listening for debug sessions.")
    }
}
