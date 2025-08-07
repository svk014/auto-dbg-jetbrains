package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.server.models.ApiResponse
import com.github.svk014.autodbgjetbrains.server.models.BreakpointInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * REST controller for debugger API endpoints
 */
class DebuggerController(private val project: Project) {

    private val debuggerService: DebuggerIntegrationService by lazy {
        project.service<DebuggerIntegrationService>()
    }

    fun configureRoutes(routing: Routing) {
        routing {
            route("/api/debugger") {
                get("/frame/{depth}") {
                    try {
                        val depth = call.parameters["depth"]?.toIntOrNull() ?: 0
                        val result = getFrame(depth)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error getting frame", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse.error("Failed to get frame: ${e.message}")
                        )
                    }
                }

                get("/variables/{frameIndex}") {
                    try {
                        val frameIndex = call.parameters["frameIndex"]?.toIntOrNull() ?: 0
                        val result = getVariables(frameIndex)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error getting variables", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse.error("Failed to get variables: ${e.message}")
                        )
                    }
                }

                get("/call-stack") {
                    try {
                        val result = getCallStack()
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error getting call stack", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse.error("Failed to get call stack: ${e.message}")
                        )
                    }
                }

                post("/breakpoint") {
                    try {
                        val file = call.request.queryParameters["file"]
                            ?: throw IllegalArgumentException("File parameter is required")
                        val line = call.request.queryParameters["line"]?.toIntOrNull()
                            ?: throw IllegalArgumentException("Line parameter is required")
                        val result = setBreakpoint(file, line)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error setting breakpoint", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("Failed to set breakpoint: ${e.message}")
                        )
                    }
                }

                get("/evaluate") {
                    try {
                        val expression = call.request.queryParameters["expression"]
                            ?: throw IllegalArgumentException("Expression parameter is required")
                        val frameIndex = call.request.queryParameters["frameIndex"]?.toIntOrNull() ?: 0
                        val result = evaluateExpression(expression, frameIndex)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error evaluating expression", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse.error("Failed to evaluate expression: ${e.message}")
                        )
                    }
                }

                post("/step/over") {
                    try {
                        val result = debuggerService.stepOver()
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("stepped" to result)))
                    } catch (e: Exception) {
                        thisLogger().error("Error in step over", e)
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error("Failed to step over: ${e.message}"))
                    }
                }
                post("/step/into") {
                    try {
                        val result = debuggerService.stepInto()
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("stepped" to result)))
                    } catch (e: Exception) {
                        thisLogger().error("Error in step into", e)
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error("Failed to step into: ${e.message}"))
                    }
                }
                post("/step/out") {
                    try {
                        val result = debuggerService.stepOut()
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("stepped" to result)))
                    } catch (e: Exception) {
                        thisLogger().error("Error in step out", e)
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error("Failed to step out: ${e.message}"))
                    }
                }
            }
        }
    }

    fun getFrame(depth: Int): ApiResponse {
        val frameInfo = debuggerService.getFrameAt(depth)
        return if (frameInfo != null) {
            ApiResponse.success(frameInfo)  // Just pass the object directly
        } else {
            ApiResponse.error("No frame available at depth $depth")
        }
    }

    fun getVariables(frameIndex: Int): ApiResponse {
        // For now, use a placeholder frameId - this will need to be improved
        // when we have proper frame ID management
        val frameId = "frame_$frameIndex"
        val variables = debuggerService.getFrameVariables(frameId)
        return ApiResponse.success(variables)
    }

    fun getCallStack(): ApiResponse {
        val callStack = debuggerService.getCallStack()
        return ApiResponse.success(callStack)
    }

    fun setBreakpoint(file: String, line: Int): ApiResponse {
        // Placeholder implementation - breakpoint functionality not yet implemented
        val breakpointInfo = BreakpointInfo(true, file, line, null, "")
        return ApiResponse.success(breakpointInfo)
    }

    fun evaluateExpression(expression: String, frameIndex: Int): ApiResponse {
        // Placeholder implementation - expression evaluation not yet implemented
        return ApiResponse.success(
            mapOf(
                "result" to "Expression evaluation not yet implemented",
                "expression" to expression
            )
        )
    }
}
