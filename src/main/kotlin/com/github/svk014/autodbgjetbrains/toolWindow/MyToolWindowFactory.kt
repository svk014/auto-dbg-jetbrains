package com.github.svk014.autodbgjetbrains.toolWindow

import com.github.svk014.autodbgjetbrains.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.xdebugger.XDebuggerManager
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JScrollPane
import javax.swing.JTextArea

class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {
        companion object {
            private var logArea: JTextArea? = null
            private val logBuffer = mutableListOf<String>()
            fun appendLog(message: String) {
                logArea?.let {
                    it.append(message + "\n")
                    it.caretPosition = it.document.length
                } ?: logBuffer.add(message)
            }
            fun flushLogs() {
                logArea?.let { area ->
                    logBuffer.forEach { msg ->
                        area.append(msg + "\n")
                    }
                    area.caretPosition = area.document.length
                    logBuffer.clear()
                }
            }
        }

        private val service = toolWindow.project.service<MyProjectService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val logTextArea = JTextArea(10, 40).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                append("[Auto DBG] Tool window loaded!\n")
            }
            logArea = logTextArea
            flushLogs()
            val sessionDropdown = JComboBox<String>()
            var sessionMap = emptyMap<String, com.intellij.xdebugger.XDebugSession>()
            val refreshButton = JButton("Refresh Debug Sessions").apply {
                addActionListener {
                    val sessions = XDebuggerManager.getInstance(toolWindow.project).debugSessions
                    sessionDropdown.removeAllItems()
                    sessionMap = sessions.associateBy { it.sessionName }
                    if (sessions.isEmpty()) {
                        appendLog("[Auto DBG] No active debug sessions.")
                    } else {
                        appendLog("[Auto DBG] Active debug sessions:")
                        sessions.forEach { session ->
                            appendLog("- ${session.sessionName} | Type: ${session.javaClass.name}")
                            sessionDropdown.addItem(session.sessionName)
                        }
                    }
                }
            }
            val connectButton = JButton("Connect").apply {
                addActionListener {
                    val selectedSessionName = sessionDropdown.selectedItem as? String
                    val selectedSession = sessionMap[selectedSessionName]
                    if (selectedSession != null) {
                        appendLog("[Auto DBG] Connected to session: $selectedSessionName")
                        // Example: pause the session (or any other action)
                        if (!selectedSession.isPaused) {
                            selectedSession.pause()
                            appendLog("[Auto DBG] Sent pause command to: $selectedSessionName")
                        } else {
                            appendLog("[Auto DBG] Session already paused: $selectedSessionName")
                        }
                    } else {
                        appendLog("[Auto DBG] No session selected to connect.")
                    }
                }
            }
            add(refreshButton)
            add(sessionDropdown)
            add(connectButton)
            add(JScrollPane(logTextArea))
        }
    }
}
