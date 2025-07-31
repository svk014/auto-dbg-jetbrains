package com.github.svk014.autodbgjetbrains.toolWindow

import com.github.svk014.autodbgjetbrains.server.DebuggerApiServer
import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

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

        private val debuggerService = toolWindow.project.service<DebuggerIntegrationService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()

            // Get the API server service
            val apiServer = toolWindow.project.service<DebuggerApiServer>()

            // Create server control panel
            val serverPanel = JPanel(GridLayout(3, 2, 5, 5)).apply {
                border = BorderFactory.createTitledBorder("REST API Server")

                val serverStatusLabel = JLabel("Status: Stopped")
                val serverUrlLabel = JLabel("URL: Not running")

                val startServerButton = JButton("Start Server").apply {
                    addActionListener {
                        apiServer.startServer()
                        // Update UI after server starts (with a small delay for startup)
                        Timer(1000) {
                            SwingUtilities.invokeLater {
                                if (apiServer.isRunning()) {
                                    serverStatusLabel.text = "Status: Running (Port: ${apiServer.getServerPort()})"
                                    serverUrlLabel.text = "URL: ${apiServer.getServerUrl()}"
                                    isEnabled = false
                                    // Find the stop button in the parent panel and enable it
                                    (parent as JPanel).components.find { it is JButton && it.text == "Stop Server" }?.isEnabled = true
                                }
                            }
                        }.apply { isRepeats = false }.start()
                    }
                }

                val stopServerButton = JButton("Stop Server").apply {
                    isEnabled = false
                    addActionListener {
                        apiServer.stopServer()
                        serverStatusLabel.text = "Status: Stopped"
                        serverUrlLabel.text = "URL: Not running"
                        isEnabled = false
                        startServerButton.isEnabled = true
                    }
                }

                val copyUrlButton = JButton("Copy API URL").apply {
                    addActionListener {
                        apiServer.getServerUrl()?.let { url ->
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            val selection = java.awt.datatransfer.StringSelection("$url/api/discovery/tools")
                            clipboard.setContents(selection, selection)
                            appendLog("[Auto DBG] API discovery URL copied to clipboard: $url/api/tools")
                        } ?: appendLog("[Auto DBG] Server is not running")
                    }
                }

                add(serverStatusLabel)
                add(startServerButton)
                add(serverUrlLabel)
                add(stopServerButton)
                add(JLabel("Discovery Endpoint:"))
                add(copyUrlButton)
            }

            // Create debug session control panel
            val debugPanel = JPanel(GridLayout(2, 2, 5, 5)).apply {
                border = BorderFactory.createTitledBorder("Debug Session Control")

                val sessionDropdown = JComboBox<String>()

                val refreshButton = JButton("Refresh Debug Sessions").apply {
                    addActionListener {
                        val sessionNames = debuggerService.refreshAndGetActiveSessionNames()
                        sessionDropdown.model = DefaultComboBoxModel(sessionNames.toTypedArray())
                        if (sessionNames.isNotEmpty()) {
                            appendLog("[Auto DBG] Found active sessions. Please select one and connect.")
                        } else {
                            appendLog("[Auto DBG] No active debug sessions found.")
                        }
                    }
                }

                val connectButton = JButton("Connect").apply {
                    addActionListener {
                        val selectedSessionName = sessionDropdown.selectedItem as? String
                        if (selectedSessionName != null) {
                            debuggerService.connectToSession(selectedSessionName)
                        } else {
                            appendLog("[Auto DBG] Error: No session selected.")
                        }
                    }
                }

                val pauseButton = JButton("Pause").apply {
                    addActionListener {
                        // The service already knows which session is connected.
                        debuggerService.pauseCurrentSession()
                    }
                }

                add(refreshButton)
                add(sessionDropdown)
                add(connectButton)
                add(pauseButton)
            }

            // Create log panel
            val logTextArea = JTextArea(15, 50).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                append("[Auto DBG] Tool window loaded!\n")
                append("[Auto DBG] Available API endpoints:\n")
                append("- GET /api/tools (Discovery)\n")
                append("- GET /api/debugger/frame/{depth}\n")
                append("- GET /api/debugger/callstack?maxDepth=10\n")
                append("- GET /api/debugger/variables?frameId=&maxDepth=3\n")
            }
            logArea = logTextArea
            flushLogs()

            val logScrollPane = JScrollPane(logTextArea).apply {
                border = BorderFactory.createTitledBorder("Logs")
            }

            // Layout the panels
            val controlsPanel = JPanel(BorderLayout()).apply {
                add(serverPanel, BorderLayout.NORTH)
                add(debugPanel, BorderLayout.CENTER)
            }

            add(controlsPanel, BorderLayout.NORTH)
            add(logScrollPane, BorderLayout.CENTER)
        }
    }
}
