package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.annotations.ApiEndpoint
import com.github.svk014.autodbgjetbrains.annotations.ApiParam
import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.server.models.ApiResponse
import com.intellij.openapi.components.service
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
                // Thin wrapper routes that delegate to annotated methods
                get("/frame/{depth}") {
                    try {
                        val depth = call.parameters["depth"]?.toIntOrNull() ?: 0
                        val result = getFrameAt(depth)

                        if (result != null) {
                            call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = result))
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse<Any>(success = false, error = "Frame not found at depth $depth")
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Any>(success = false, error = e.message ?: "Unknown error")
                        )
                    }
                }

                // Get call stack
                get("/callstack") {
                    try {
                        val maxDepth = call.request.queryParameters["maxDepth"]?.toIntOrNull() ?: 10
                        val result = getCallStack(maxDepth)
                        call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = result))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Any>(success = false, error = e.message ?: "Unknown error")
                        )
                    }
                }

                // Get frame variables
                get("/variables") {
                    try {
                        val frameId = call.request.queryParameters["frameId"] ?: "0"
                        val maxDepth = call.request.queryParameters["maxDepth"]?.toIntOrNull() ?: 3
                        val result = getVariables(frameId, maxDepth)
                        call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = result))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Any>(success = false, error = e.message ?: "Unknown error")
                        )
                    }
                }

                // Annotation-based API endpoints

                post("/variable/set") {
                    try {
                        // Parse request body for variable setting
                        val variableName = call.request.queryParameters["name"] ?: ""
                        val value = call.request.queryParameters["value"] ?: ""
                        val frameDepth = call.request.queryParameters["frameDepth"]?.toIntOrNull() ?: 0

                        val result = setVariable(variableName, value, frameDepth)
                        call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = result))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Any>(success = false, error = e.message ?: "Unknown error")
                        )
                    }
                }

                post("/breakpoint") {
                    try {
                        val filePath = call.request.queryParameters["filePath"] ?: ""
                        val lineNumber = call.request.queryParameters["lineNumber"]?.toIntOrNull() ?: 0
                        val condition = call.request.queryParameters["condition"]

                        val result = setBreakpoint(filePath, lineNumber, condition)
                        call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = result))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Any>(success = false, error = e.message ?: "Unknown error")
                        )
                    }
                }
            }
        }
    }

    @ApiEndpoint(
        name = "getFrameAt",
        method = "GET",
        path = "/api/debugger/frame/{depth}",
        description = "Retrieve stack frame information at a specific depth (0 = top frame)",
        returnType = "FrameInfo"
    )
    fun getFrameAt(
        @ApiParam(
            description = "The depth of the stack frame to retrieve",
            type = "integer"
        ) depth: Int
    ) = debuggerService.getFrameAt(depth)

    @ApiEndpoint(
        name = "getCallStack",
        method = "GET",
        path = "/api/debugger/callstack",
        description = "Get the complete call stack up to a specified depth",
        returnType = "List<FrameInfo>"
    )
    fun getCallStack(
        @ApiParam(
            description = "Maximum depth of call stack to retrieve",
            type = "integer",
            required = false
        ) maxDepth: Int = 10
    ) = debuggerService.getCallStack(maxDepth)

    @ApiEndpoint(
        name = "getVariables",
        method = "GET",
        path = "/api/debugger/variables",
        description = "Get all variables in scope at a specific frame ID",
        returnType = "Map<String, Variable>"
    )
    fun getVariables(
        @ApiParam(
            description = "Frame ID to get variables from",
            type = "string",
            required = false
        ) frameId: String = "0",
        @ApiParam(
            description = "Maximum depth for variable extraction",
            type = "integer",
            required = false
        ) maxDepth: Int = 3
    ) = debuggerService.getFrameVariables(frameId, maxDepth)

    @ApiEndpoint(
        name = "setVariable",
        method = "POST",
        path = "/api/debugger/variable/set",
        description = "Set the value of a variable in the current debugging context",
        returnType = "OperationResult"
    )
    fun setVariable(
        @ApiParam(
            description = "Name of the variable to set",
            type = "string"
        ) variableName: String,
        @ApiParam(
            description = "New value for the variable",
            type = "string"
        ) value: String,
        @ApiParam(
            description = "Frame depth where the variable exists",
            type = "integer",
            required = false
        ) frameDepth: Int = 0
    ): Map<String, Any> {
        // TODO: Implement variable setting in DebuggerIntegrationService
        return mapOf(
            "success" to true,
            "variable" to variableName,
            "newValue" to value,
            "frameDepth" to frameDepth,
            "message" to "Variable setting not yet implemented"
        )
    }

    @ApiEndpoint(
        name = "setBreakpoint",
        method = "POST",
        path = "/api/debugger/breakpoint",
        description = "Set a breakpoint at a specific file and line",
        returnType = "BreakpointInfo"
    )
    fun setBreakpoint(
        @ApiParam(
            description = "File path where to set the breakpoint",
            type = "string"
        ) filePath: String,
        @ApiParam(
            description = "Line number for the breakpoint",
            type = "integer"
        ) lineNumber: Int,
        @ApiParam(
            description = "Optional condition for the breakpoint",
            type = "string",
            required = false
        ) condition: String? = null
    ): Map<String, Any> {
        // TODO: Implement breakpoint setting in DebuggerIntegrationService
        return mapOf(
            "success" to true,
            "filePath" to filePath,
            "lineNumber" to lineNumber,
            "condition" to (condition ?: "none"),
            "message" to "Breakpoint setting not yet implemented"
        )
    }
}
