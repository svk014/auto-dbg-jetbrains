package com.github.svk014.autodbgjetbrains.toolWindow

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.server.DebuggerApiServer
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
            val serverPanel = JPanel(GridLayout(3, 2, 5, 5))
            val serverStatusLabel = JLabel("Status: Stopped")
            val serverUrlLabel = JLabel("URL: Not running")
            val startServerButton = JButton("Start Server")
            val stopServerButton = JButton("Stop Server").apply { isEnabled = false }
            val copyUrlButton = JButton("Copy API URL")

            // Create API endpoints dropdown panel
            val apiPanel = JPanel(BorderLayout())
            val apiDropdown = JComboBox<String>()
            val copyApiButton = JButton("Copy Endpoint")
            copyApiButton.isEnabled = false
            apiDropdown.isEnabled = false
            apiPanel.border = BorderFactory.createTitledBorder("Available API Endpoints")
            apiPanel.add(apiDropdown, BorderLayout.CENTER)
            apiPanel.add(copyApiButton, BorderLayout.EAST)

            fun updateApiList(baseUrl: String?) {
                if (baseUrl != null) {
                    val endpoints = listOf(
                        "$baseUrl/api/debugger/frame/{depth}",
                        "$baseUrl/api/debugger/variables/{frameIndex}",
                        "$baseUrl/api/debugger/call-stack",
                        "$baseUrl/api/debugger/breakpoint (POST)",
                        "$baseUrl/api/debugger/evaluate"
                    )
                    apiDropdown.model = DefaultComboBoxModel(endpoints.toTypedArray())
                    apiDropdown.isEnabled = true
                    copyApiButton.isEnabled = true
                } else {
                    apiDropdown.model = DefaultComboBoxModel(arrayOf("Server not running"))
                    apiDropdown.isEnabled = false
                    copyApiButton.isEnabled = false
                }
            }
            // Expose for serverPanel to update
            (apiPanel as JComponent).putClientProperty("updateApiList", ::updateApiList)

            // API copy button
            copyApiButton.addActionListener {
                val selected = apiDropdown.selectedItem as? String
                if (selected != null) {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val selection = java.awt.datatransfer.StringSelection(selected)
                    clipboard.setContents(selection, selection)
                    appendLog("[Auto DBG] API endpoint copied to clipboard: $selected")
                }
            }

            // Server copy button
            copyUrlButton.addActionListener {
                val url = apiServer.getServerUrl()
                if (url != null) {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val selection = java.awt.datatransfer.StringSelection(url)
                    clipboard.setContents(selection, selection)
                    appendLog("[Auto DBG] API base URL copied to clipboard: $url")
                } else {
                    appendLog("[Auto DBG] Server is not running")
                }
            }

            // UI update function
            fun updateUiForServerState() {
                if (apiServer.isRunning()) {
                    serverStatusLabel.text = "Status: Running (Port: ${apiServer.getServerPort()})"
                    serverUrlLabel.text = "URL: ${apiServer.getServerUrl()}"
                    startServerButton.isEnabled = false
                    stopServerButton.isEnabled = true
                    updateApiList(apiServer.getServerUrl())
                } else {
                    serverStatusLabel.text = "Status: Stopped"
                    serverUrlLabel.text = "URL: Not running"
                    startServerButton.isEnabled = true
                    stopServerButton.isEnabled = false
                    updateApiList(null)
                }
            }

            // Server button listeners
            startServerButton.addActionListener {
                apiServer.startServer()
                Timer(1000) {
                    SwingUtilities.invokeLater {
                        updateUiForServerState()
                    }
                }.apply { isRepeats = false }.start()
            }
            stopServerButton.addActionListener {
                apiServer.stopServer()
                updateUiForServerState()
            }

            // Add serverPanel components
            serverPanel.border = BorderFactory.createTitledBorder("REST API Server")
            serverPanel.add(serverStatusLabel)
            serverPanel.add(startServerButton)
            serverPanel.add(serverUrlLabel)
            serverPanel.add(stopServerButton)
            serverPanel.add(JLabel("Discovery Endpoint:"))
            serverPanel.add(copyUrlButton)

            // Initial UI sync
            updateUiForServerState()

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
            }
            logArea = logTextArea
            flushLogs()

            val logScrollPane = JScrollPane(logTextArea).apply {
                border = BorderFactory.createTitledBorder("Logs")
            }

            // Layout the panels
            val controlsPanel = JPanel(BorderLayout()).apply {
                add(serverPanel, BorderLayout.NORTH)
                add(apiPanel, BorderLayout.CENTER)
                add(debugPanel, BorderLayout.SOUTH)
            }

            add(controlsPanel, BorderLayout.NORTH)
            add(logScrollPane, BorderLayout.CENTER)
        }
    }
}
