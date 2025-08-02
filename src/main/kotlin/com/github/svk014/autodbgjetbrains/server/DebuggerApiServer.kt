package com.github.svk014.autodbgjetbrains.server

import com.github.svk014.autodbgjetbrains.server.controllers.DebuggerController
import com.github.svk014.autodbgjetbrains.toolWindow.MyToolWindowFactory.MyToolWindow.Companion.appendLog
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * REST API server for exposing debugger functionality to external MCP servers
 */
@Service(Service.Level.PROJECT)
class DebuggerApiServer(private val project: Project) {

    private var server: NettyApplicationEngine? = null
    private var actualPort: Int? = null

    init {
        startServerInternal()
    }

    /**
     * Internal server startup logic
     */
    private fun startServerInternal() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(Netty, port = 0) { // port = 0 means random available port
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                        })
                    }

                    routing {
                        // Register debugger controller routes (handles /api/debugger/* endpoints)
                        val debuggerController = DebuggerController(project)
                        debuggerController.configureRoutes(this)
                    }
                }.start(wait = false)

                // Get the actual port assigned by the system
                actualPort = server?.resolvedConnectors()?.firstOrNull()?.port

                thisLogger().info("[Auto DBG] HTTP API Server started on http://localhost:$actualPort")
                appendLog("[Auto DBG] HTTP API Server started on http://localhost:$actualPort")
                appendLog("[Auto DBG] Available endpoints:")
                appendLog("  - GET  http://localhost:$actualPort/api/debugger/frame/{depth}")
                appendLog("  - GET  http://localhost:$actualPort/api/debugger/callstack")
                appendLog("  - GET  http://localhost:$actualPort/api/debugger/variables")
                appendLog("  - POST http://localhost:$actualPort/api/debugger/variable/set")
                appendLog("  - POST http://localhost:$actualPort/api/debugger/breakpoint")

            } catch (e: Exception) {
                thisLogger().error("[Auto DBG] Failed to start HTTP server", e)
                appendLog("[Auto DBG] Failed to start HTTP server: ${e.message}")
            }
        }
    }

    /**
     * Check if the server is currently running
     */
    fun isRunning(): Boolean = server != null && actualPort != null

    /**
     * Get the server port (for backward compatibility with tool window)
     */
    fun getServerPort(): Int? = actualPort

    /**
     * Get the full server URL (for backward compatibility with tool window)
     */
    fun getServerUrl(): String? = actualPort?.let { "http://localhost:$it" }

    /**
     * Start server method (for backward compatibility with tool window)
     * Since server auto-starts in init, this just reports status
     */
    fun startServer() {
        if (!isRunning()) {
            // Server should have started in init, but if not, try starting again
            startServerInternal()
        } else {
            appendLog("[Auto DBG] Server is already running on port $actualPort")
        }
    }

    /**
     * Get the actual port the server is running on
     */
    fun getPort(): Int? = actualPort

    /**
     * Stop the API server
     */
    fun stopServer() {
        server?.stop(1000, 2000)
        server = null
        actualPort = null
        thisLogger().info("[Auto DBG] HTTP API Server stopped")
        appendLog("[Auto DBG] HTTP API Server stopped")
    }
}
