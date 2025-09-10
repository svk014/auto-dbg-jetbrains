package com.github.svk014.autodbgjetbrains.toolWindow

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.server.DebuggerApiServer
import com.github.svk014.autodbgjetbrains.server.controllers.DebuggerController
import com.github.svk014.autodbgjetbrains.models.ApiRoute
import com.github.svk014.autodbgjetbrains.models.FieldType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseWheelEvent
import javax.swing.*

class MyToolWindowFactory : ToolWindowFactory {

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

            // Dark theme colors
            private val DARK_BG = Color(0x2B2B2B)
            private val SECTION_BG = Color(0x3C3F41)
            private val TEXT_COLOR = Color(0xBBBBBB)
            private val HEADER_COLOR = Color(0xE6E6E6)
            private val GREEN_COLOR = Color(0x4CAF50)
            private val RED_COLOR = Color(0xF44336)
            private val BLUE_COLOR = Color(0x2196F3)
            private val BORDER_COLOR = Color(0x555555)

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

            // Custom button class that extends JComponent
            private class StyledButton(
                private val text: String, private val bgColor: Color, tooltipText: String? = null
            ) : JComponent() {
                private var isHovered = false
                private var isPressed = false
                private val actionListeners = mutableListOf<java.awt.event.ActionListener>()
                private var isButtonEnabled = true

                init {
                    preferredSize = Dimension(100, 36)
                    minimumSize = Dimension(80, 36)
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    tooltipText?.let { toolTipText = it }
                    isOpaque = false

                    // Add mouse listeners for hover and click effects
                    addMouseListener(object : java.awt.event.MouseAdapter() {
                        override fun mouseEntered(e: java.awt.event.MouseEvent) {
                            if (isButtonEnabled) {
                                isHovered = true
                                repaint()
                            }
                        }

                        override fun mouseExited(e: java.awt.event.MouseEvent) {
                            isHovered = false
                            repaint()
                        }

                        override fun mousePressed(e: java.awt.event.MouseEvent) {
                            if (isButtonEnabled) {
                                isPressed = true
                                repaint()
                            }
                        }

                        override fun mouseReleased(e: java.awt.event.MouseEvent) {
                            if (isButtonEnabled && isPressed) {
                                isPressed = false
                                repaint()
                                val event = java.awt.event.ActionEvent(
                                    this@StyledButton, java.awt.event.ActionEvent.ACTION_PERFORMED, text
                                )
                                actionListeners.forEach { it.actionPerformed(event) }
                            }
                        }
                    })
                }

                fun addActionListener(listener: java.awt.event.ActionListener) {
                    actionListeners.add(listener)
                }

                override fun setEnabled(enabled: Boolean) {
                    isButtonEnabled = enabled
                    cursor = if (enabled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                    repaint()
                }

                override fun isEnabled(): Boolean = isButtonEnabled

                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                    // Calculate colors based on state
                    val currentBgColor = when {
                        !isButtonEnabled -> Color(bgColor.red / 2, bgColor.green / 2, bgColor.blue / 2)
                        isPressed -> bgColor.darker()
                        isHovered -> Color(
                            minOf(255, (bgColor.red * 1.2).toInt()),
                            minOf(255, (bgColor.green * 1.2).toInt()),
                            minOf(255, (bgColor.blue * 1.2).toInt())
                        )

                        else -> bgColor
                    }

                    // Paint background
                    g2.color = currentBgColor
                    g2.fillRoundRect(0, 0, width, height, 8, 8)

                    // Paint text
                    g2.color = if (isButtonEnabled) Color.WHITE else Color.LIGHT_GRAY
                    g2.font = font.deriveFont(Font.BOLD, 12f)
                    val fm = g2.fontMetrics
                    val textWidth = fm.stringWidth(text)
                    val x = (width - textWidth) / 2
                    val y = (height - fm.height) / 2 + fm.ascent
                    g2.drawString(text, x, y)

                    g2.dispose()
                }
            }

            private fun createStyledButton(text: String, bgColor: Color, tooltipText: String? = null): StyledButton {
                return StyledButton(text, bgColor, tooltipText)
            }

            private fun createSection(title: String, content: JPanel): JPanel {
                return JPanel().apply {
                    layout = BorderLayout()
                    background = SECTION_BG

                    // Create header
                    val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                        background = SECTION_BG
                        val headerLabel = JLabel(title).apply {
                            foreground = HEADER_COLOR
                            font = font.deriveFont(Font.BOLD, 14f)
                        }
                        add(headerLabel)
                    }

                    // Add content with padding - removed horizontal padding
                    val paddedContent = JPanel(BorderLayout()).apply {
                        background = SECTION_BG
                        border = BorderFactory.createEmptyBorder(16, 0, 24, 0) // Only top and bottom padding
                        add(content, BorderLayout.CENTER)
                    }

                    add(headerPanel, BorderLayout.NORTH)
                    add(paddedContent, BorderLayout.CENTER)

                    // Removed rounded border and shadow - only basic padding remains
                    border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
                }
            }
        }

        private val debuggerService = toolWindow.project.service<DebuggerIntegrationService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            background = DARK_BG

            // Get the API server service
            val apiServer = toolWindow.project.service<DebuggerApiServer>()

            // Main container with vertical layout - make it scrollable
            val mainPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = DARK_BG
                border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            }

            // 1. REST API Server Section - Fix layout and remove redundant text
            val serverStatusLabel = JLabel().apply {
                foreground = HEADER_COLOR
                font = font.deriveFont(Font.PLAIN, 11f) // Use smaller plain font instead of HTML
                // Set fixed dimensions upfront to prevent any layout shifts
                preferredSize = Dimension(70, 16)
                minimumSize = Dimension(70, 16)
                maximumSize = Dimension(70, 16)
                horizontalAlignment = SwingConstants.LEFT
            }

            // Function to update server status with plain text (no HTML)
            fun updateServerStatusLabel(isRunning: Boolean) {
                if (isRunning) {
                    serverStatusLabel.text = "🟢 Running"
                } else {
                    serverStatusLabel.text = "⭕ Stopped"
                }
                // Force revalidation to ensure size constraints are respected
                serverStatusLabel.revalidate()
            }

            // Initialize with server status
            updateServerStatusLabel(apiServer.isRunning())

            val portLabel =
                JLabel("Port: ${if (apiServer.isRunning()) apiServer.getServerPort() else "Not running"}").apply {
                    foreground = TEXT_COLOR
                    font = font.deriveFont(12f)
                }

            val urlPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 4)).apply {
                background = SECTION_BG
                val urlLabel = JLabel("URL: ${apiServer.getServerUrl() ?: "Not running"}").apply {
                    foreground = TEXT_COLOR
                    font = font.deriveFont(12f)
                }
                val copyUrlButton = createStyledButton("\uD83D\uDCC4 Copy", BLUE_COLOR, "Copy URL to clipboard")

                copyUrlButton.addActionListener {
                    val url = apiServer.getServerUrl()
                    if (url != null) {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        val selection = StringSelection(url)
                        clipboard.setContents(selection, selection)
                        appendLog("[Auto DBG] API base URL copied to clipboard: $url")
                    } else {
                        appendLog("[Auto DBG] Server is not running")
                    }
                }

                add(urlLabel)
                add(Box.createHorizontalStrut(12))
                add(copyUrlButton)
            }

            val serverButtonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply {
                background = SECTION_BG
                val startButton = createStyledButton("▶ Start", GREEN_COLOR, "Start Server")
                val stopButton = createStyledButton("■ Stop", RED_COLOR, "Stop Server")

                startButton.addActionListener {
                    apiServer.startServer()
                    Timer(1000) {
                        SwingUtilities.invokeLater {
                            updateServerUI(
                                apiServer, portLabel, urlPanel, startButton, stopButton, ::updateServerStatusLabel
                            )
                        }
                    }.apply { isRepeats = false }.start()
                }

                stopButton.addActionListener {
                    apiServer.stopServer()
                    updateServerUI(apiServer, portLabel, urlPanel, startButton, stopButton, ::updateServerStatusLabel)
                }

                add(startButton)
                add(Box.createHorizontalStrut(8))
                add(stopButton)
            }

            val serverContentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = SECTION_BG

                // Create a panel for status with left alignment
                val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    background = SECTION_BG
                    add(serverStatusLabel)
                }

                // Create a panel for port with left alignment
                val portPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    background = SECTION_BG
                    add(portLabel)
                }

                // API Explorer dropdown and copy button moved here
                val apiEndpoints = DebuggerController.routes

                val apiDropdown = JComboBox(apiEndpoints.toTypedArray()).apply {
                    background = Color(0x4C4C4C)
                    foreground = TEXT_COLOR
                    font = Font("Courier New", Font.PLAIN, 12)
                    border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
                }

                // Panel for dynamic param fields
                val paramPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply {
                    background = SECTION_BG
                }

                // Panel for request body (only for POST)
                val bodyPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
                    background = SECTION_BG
                }

                fun updateBodyFields(route: ApiRoute) {
                    bodyPanel.removeAll()
                    for (field in route.bodyFields) {
                        bodyPanel.add(JLabel("${field.name}: ").apply { foreground = TEXT_COLOR })
                        val defaultVal = field.defaultValue?.toString() ?: ""
                        val input = JTextField(defaultVal, 12).apply {
                            name = field.name
                            background = Color(0x4C4C4C)
                            foreground = TEXT_COLOR
                        }

                        bodyPanel.add(input)
                    }
                    bodyPanel.isVisible = route.bodyFields.isNotEmpty()
                    bodyPanel.revalidate()
                    bodyPanel.repaint()
                }


                // Update paramPanel fields based on selected endpoint
                fun updateParamFields(route: ApiRoute) {
                    paramPanel.removeAll()
                    val params = route.params
                    for (param in params) {
                        paramPanel.add(JLabel("$param: ").apply { foreground = TEXT_COLOR })
                        paramPanel.add(JTextField(8).apply {
                            name = param
                            background = Color(0x4C4C4C)
                            foreground = TEXT_COLOR
                        })
                    }
                    paramPanel.isVisible = params.isNotEmpty()
                    paramPanel.revalidate()
                    paramPanel.repaint()
                }

                apiDropdown.addActionListener {
                    val route = apiDropdown.selectedItem as? ApiRoute ?: return@addActionListener
                    updateParamFields(route)
                    updateBodyFields(route)
                }
                // Initialize param fields for first endpoint
                updateParamFields(apiEndpoints[0])

                val runRequestButton = createStyledButton("▶ Run Request", GREEN_COLOR, "Run selected API request")
                runRequestButton.addActionListener {
                    val selected = apiDropdown.selectedItem as? ApiRoute ?: return@addActionListener
                    val params = selected.params
                    var endpoint = selected.path
                    for (param in params) {
                        val field = paramPanel.components.find { it is JTextField && it.name == param } as? JTextField
                        val value = field?.text ?: ""
                        endpoint = endpoint.replace("{$param}", value)
                    }
                    val baseUrl = apiServer.getServerUrl() ?: "http://localhost:8080"
                    val fullUrl = "$baseUrl$endpoint"

                    val method = selected.method
                    Thread {
                        try {
                            val url = java.net.URL(fullUrl)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = method
                            conn.connectTimeout = 3000
                            conn.readTimeout = 5000
                            if (method == "POST") {
                                conn.doOutput = true
                                conn.setRequestProperty("Content-Type", "application/json")

                                val bodyJson = buildString {
                                    append("{")
                                    selected.bodyFields.forEachIndexed { i, field ->
                                        val fieldInput = bodyPanel.components.filterIsInstance<JTextField>()
                                            .find { it.name == field.name }
                                        val value = fieldInput?.text ?: field.defaultValue

                                        val jsonValue = when {
                                            value == null || (value is String && value.isEmpty()) -> null
                                            field.type == FieldType.STRING -> "\"$value\""
                                            field.type == FieldType.NUMBER -> value
                                            field.type == FieldType.BOOLEAN -> (value as String?)?.lowercase()
                                            else -> null
                                        }

                                        append("\"${field.name}\": $jsonValue")
                                        if (i < selected.bodyFields.lastIndex) append(", ")
                                    }
                                    append("}")
                                }

                                conn.outputStream.use { it.write(bodyJson.toByteArray()) }
                            }
                            val response = conn.inputStream.bufferedReader().readText()
                            SwingUtilities.invokeLater {
                                appendLog("[Auto DBG] [$method] $fullUrl\n$response")
                            }
                        } catch (ex: Exception) {
                            SwingUtilities.invokeLater {
                                appendLog("[Auto DBG] Request failed: ${ex.message}")
                            }
                        }
                    }.start()
                }

                val apiExplorerPanel = JPanel()
                apiExplorerPanel.layout = BoxLayout(apiExplorerPanel, BoxLayout.Y_AXIS)
                apiExplorerPanel.background = SECTION_BG
                val apiDropdownPanel = JPanel(BorderLayout(8, 0)).apply {
                    background = SECTION_BG
                    add(apiDropdown, BorderLayout.CENTER)
                    val copyApiButton = createStyledButton("\uD83D\uDCC4 Copy", BLUE_COLOR, "Copy API endpoint")
                    copyApiButton.addActionListener {
                        val selectedEndpoint = apiDropdown.selectedItem?.toString()
                        if (selectedEndpoint != null) {
                            val baseUrl = apiServer.getServerUrl() ?: "http://localhost:8080"
                            val endpoint = selectedEndpoint.substringAfter(" ")
                            val fullUrl = "$baseUrl$endpoint"
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            val selection = StringSelection(fullUrl)
                            clipboard.setContents(selection, selection)
                            appendLog("[Auto DBG] API endpoint copied to clipboard: $fullUrl")
                        }
                    }
                    add(copyApiButton, BorderLayout.EAST)
                }

                // Hook dropdown selection
                apiDropdown.addActionListener {
                    val selected = apiDropdown.selectedItem as? ApiRoute ?: return@addActionListener
                    updateParamFields(selected)
                    updateBodyFields(selected)
                }

                apiExplorerPanel.add(apiDropdownPanel)
                apiExplorerPanel.add(Box.createVerticalStrut(8))
                apiExplorerPanel.add(paramPanel)
                apiExplorerPanel.add(Box.createVerticalStrut(8))
                apiExplorerPanel.add(bodyPanel)
                apiExplorerPanel.add(Box.createVerticalStrut(8))
                add(statusPanel)
                add(Box.createVerticalStrut(8))
                add(portPanel)
                add(Box.createVerticalStrut(8))
                add(urlPanel)
                add(Box.createVerticalStrut(8))
                add(apiExplorerPanel)
                add(Box.createVerticalStrut(8))
                serverButtonsPanel.add(Box.createHorizontalStrut(8))
                serverButtonsPanel.add(runRequestButton)
                add(serverButtonsPanel)
            }

            // 2. Debug Session Section
            val sessionDropdown = JComboBox<String>().apply {
                background = Color(0x4C4C4C)
                foreground = TEXT_COLOR
                border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            }

            val sessionControlsPanel = JPanel(BorderLayout(8, 8)).apply { // Reduced gap from 12 to 8
                background = SECTION_BG

                // Make dropdown take up remaining width with smaller minimum
                sessionDropdown.apply {
                    preferredSize = Dimension(120, preferredSize.height) // Reduced from 200 to 120
                    minimumSize = Dimension(80, preferredSize.height) // Reduced from 150 to 80
                }

                add(sessionDropdown, BorderLayout.CENTER)

                val refreshButton = createStyledButton("↻ Refresh", BLUE_COLOR, "Refresh debug sessions")
                refreshButton.addActionListener {
                    val sessionNames = debuggerService.refreshAndGetActiveSessionNames()
                    sessionDropdown.model = DefaultComboBoxModel(sessionNames.toTypedArray())
                    if (sessionNames.isNotEmpty()) {
                        appendLog("[Auto DBG] Found active sessions. Please select one and connect.")
                    } else {
                        appendLog("[Auto DBG] No active debug sessions found.")
                    }
                }
                add(refreshButton, BorderLayout.EAST)
            }

            val sessionButtonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply {
                background = SECTION_BG
                val connectButton = createStyledButton("⇄ Connect", GREEN_COLOR, "Connect to selected session")
                val pauseButton = createStyledButton("⏸ Pause", Color(0xFF9800), "Pause current session")

                connectButton.addActionListener {
                    val selectedSessionName = sessionDropdown.selectedItem as? String
                    if (selectedSessionName != null) {
                        debuggerService.connectToSession(selectedSessionName)
                    } else {
                        appendLog("[Auto DBG] Error: No session selected.")
                    }
                }

                pauseButton.addActionListener {
                    debuggerService.pauseCurrentSession()
                }

                add(connectButton)
                add(Box.createHorizontalStrut(8))
                add(pauseButton)
            }

            val debugContentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = SECTION_BG
                add(sessionControlsPanel)
                add(Box.createVerticalStrut(8))
                add(sessionButtonsPanel)
            }

            // 4. Logs Section
            val logTextArea = JTextArea().apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                background = Color(0x1E1E1E)
                foreground = TEXT_COLOR
                font = Font("Courier New", Font.PLAIN, 11)
                border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
                append("[Auto DBG] Available endpoints loaded.\n")
                append("[Auto DBG] Found session: actual.random_company1.Main\n")
                append("[Auto DBG] Connected to actual.random_company1.Main (JAVA).\n")
                append("[Auto DBG] API endpoint copied to clipboard.\n")
            }
            logArea = logTextArea
            flushLogs()

            val logScrollPane = JScrollPane(logTextArea).apply {
                preferredSize = Dimension(0, 200)
                maximumSize = Dimension(Int.MAX_VALUE, 200)
                border = BorderFactory.createEmptyBorder()
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }

            val logContentPanel = JPanel(BorderLayout()).apply {
                background = SECTION_BG
                add(logScrollPane, BorderLayout.CENTER)
            }

            // Add all sections to the main panel - removed API Explorer section
            mainPanel.add(createSection("REST API Server", serverContentPanel))
            mainPanel.add(Box.createVerticalStrut(16))
            mainPanel.add(createSection("Debug Session", debugContentPanel))
            mainPanel.add(Box.createVerticalStrut(16))
            mainPanel.add(createSection("Logs", logContentPanel))

            // Wrap the main panel in a scroll pane to make the entire page scrollable with optimized performance
            val scrollPane = JScrollPane(mainPanel).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                border = BorderFactory.createEmptyBorder()
                background = DARK_BG
                viewport.background = DARK_BG

                // Performance optimizations for smoother scrolling
                viewport.scrollMode = JViewport.BACKINGSTORE_SCROLL_MODE // Use backing store for better performance
                verticalScrollBar.unitIncrement = 16 // Smoother scroll increments
                verticalScrollBar.blockIncrement = 64 // Faster page scrolling

                // Enable double buffering for smoother rendering
                isDoubleBuffered = true
                viewport.isDoubleBuffered = true

                // Optimize viewport for faster repaints
                viewport.putClientProperty("JViewport.isPaintingOrigin", true)

                // Set preferred viewport size to avoid unnecessary layout calculations
                viewport.preferredSize = null // Let it calculate naturally

                // Add mouse wheel scroll optimization
                addMouseWheelListener { e ->
                    if (e.scrollType == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                        val scrollAmount = e.scrollAmount * e.wheelRotation * 16
                        val currentValue = verticalScrollBar.value
                        val newValue = (currentValue + scrollAmount).coerceIn(
                            verticalScrollBar.minimum, verticalScrollBar.maximum - verticalScrollBar.visibleAmount
                        )
                        verticalScrollBar.value = newValue
                        e.consume()
                    }
                }
            }

            add(scrollPane, BorderLayout.CENTER)

            // Initialize UI state
            updateServerUI(
                apiServer,
                portLabel,
                urlPanel,
                serverButtonsPanel.components[0] as JComponent,
                serverButtonsPanel.components[2] as JComponent,
                ::updateServerStatusLabel
            )
        }

        private fun updateServerUI(
            apiServer: DebuggerApiServer,
            portLabel: JLabel,
            urlPanel: JPanel,
            startButton: JComponent,
            stopButton: JComponent,
            updateStatusLabel: (Boolean) -> Unit
        ) {
            if (apiServer.isRunning()) {
                // Update status using the callback function
                updateStatusLabel(true)

                portLabel.text = "Port: ${apiServer.getServerPort()}"

                // Update URL in the URL panel
                val urlLabel = urlPanel.components[0] as JLabel
                urlLabel.text = "URL: ${apiServer.getServerUrl()}"

                startButton.isEnabled = false
                stopButton.isEnabled = true
            } else {
                // Update status using the callback function
                updateStatusLabel(false)

                portLabel.text = "Port: Not running"

                val urlLabel = urlPanel.components[0] as JLabel
                urlLabel.text = "URL: Not running"

                startButton.isEnabled = true
                stopButton.isEnabled = false
            }
        }
    }
}
