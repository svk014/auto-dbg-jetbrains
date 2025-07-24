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
                val msg = buildString {
                    append("[Auto DBG] Debug session started: ")
                    append(session.sessionName)
                    append(" | Type: ")
                    append(session.javaClass.name)
                    append(" | DebugProcess: ")
                    append(debugProcess.javaClass.name)
                }
                thisLogger().info(msg)
                appendLog(msg)
                debugProcess.session.addSessionListener(object : XDebugSessionListener {
                    override fun sessionPaused() {
                        thisLogger().info("[Auto DBG] Debug session paused")
                        appendLog("[Auto DBG] Debug session paused")
                    }
                    override fun sessionResumed() {
                        thisLogger().info("[Auto DBG] Debug session resumed")
                        appendLog("[Auto DBG] Debug session resumed")
                    }
                    override fun sessionStopped() {
                        thisLogger().info("[Auto DBG] Debug session stopped")
                        appendLog("[Auto DBG] Debug session stopped")
                    }
                })
            }
        })
        thisLogger().info("[Auto DBG] DebuggerIntegrationService initialized and listening for debug sessions.")
        appendLog("[Auto DBG] DebuggerIntegrationService initialized and listening for debug sessions.")
    }
}
