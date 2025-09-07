package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.debugger.DebuggerIntegrationService
import com.github.svk014.autodbgjetbrains.models.SourceLine
import com.github.svk014.autodbgjetbrains.models.ApiResponse
import com.github.svk014.autodbgjetbrains.models.FieldType
import com.github.svk014.autodbgjetbrains.models.ApiRoute
import com.github.svk014.autodbgjetbrains.models.ApiField
import com.github.svk014.autodbgjetbrains.models.BreakpointType
import com.github.svk014.autodbgjetbrains.models.JavaBreakpointType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.github.svk014.autodbgjetbrains.models.ParsedBreakpointRequest

/**
 * REST controller for debugger API endpoints
 */
class DebuggerController(private val project: Project) {

    private val debuggerService: DebuggerIntegrationService by lazy {
        project.service<DebuggerIntegrationService>()
    }

    companion object {
        val routes = listOf(
            ApiRoute.of("GET", "/api/debugger/frame/{depth}"),
            ApiRoute.of("GET", "/api/debugger/variables/{frameIndex}"),
            ApiRoute.of("GET", "/api/debugger/call-stack"),
            ApiRoute.of("GET", "/api/debugger/breakpoint"),
            ApiRoute.of(
                "POST", "/api/debugger/breakpoint", listOf(
                    ApiField("file", FieldType.STRING, required = true),
                    ApiField("line", FieldType.NUMBER, required = true),
                    ApiField("breakpointType", FieldType.STRING, required = true, defaultValue = "LINE"),
                    ApiField("lambdaOrdinal", FieldType.NUMBER),
                )
            ),
            ApiRoute.of(
                "POST", "/api/debugger/breakpoint/remove", listOf(
                    ApiField("file", FieldType.STRING, required = true),
                    ApiField("line", FieldType.NUMBER, required = true),
                    ApiField("breakpointType", FieldType.STRING, required = true, defaultValue = "LINE"),
                    ApiField("lambdaOrdinal", FieldType.NUMBER),
                )
            ),
            ApiRoute.of(
                "POST", "/api/debugger/evaluate", listOf(
                    ApiField("expression", FieldType.STRING, required = true),
                    ApiField("frameIndex", FieldType.NUMBER, required = false, defaultValue = 0)
                )
            ),
            ApiRoute.of("POST", "/api/debugger/step/over"),
            ApiRoute.of("POST", "/api/debugger/step/into"),
            ApiRoute.of("POST", "/api/debugger/step/out"),
            ApiRoute.of("POST", "/api/debugger/continueExecution")
        )
    }

    suspend fun parseBreakpointReqBody(call: ApplicationCall): ParsedBreakpointRequest {
        val body = call.receive<JsonObject>()
        val file =
            body["file"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("File parameter is required")
        val line =
            body["line"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("Line parameter is required")
        val lambdaOrdinal = body["lambdaOrdinal"]?.jsonPrimitive?.intOrNull
        val breakPointType = body["breakpointType"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Breakpoint Type is required")


        return ParsedBreakpointRequest(
            file = file,
            line = line,
            lambdaOrdinal = lambdaOrdinal,
            breakPointType = JavaBreakpointType.valueOf(breakPointType)
        )
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
                            HttpStatusCode.InternalServerError, ApiResponse.error("Failed to get frame: ${e.message}")
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
                get("/breakpoint") {
                    try {
                        val result = debuggerService.getAllBreakpoints()
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error getting all breakpoints", e)
                        call.respond(
                            HttpStatusCode.BadRequest, ApiResponse.error("Failed to get breakpoints: ${e.message}")
                        )
                    }
                }
                post("/breakpoint") {
                    try {
                        val body = parseBreakpointReqBody(call)
                        val result = setBreakpoint(body.file, body.line, body.breakPointType, body.lambdaOrdinal)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error setting breakpoint", e)
                        call.respond(
                            HttpStatusCode.BadRequest, ApiResponse.error("Failed to set breakpoint: ${e.message}")
                        )
                    }
                }
                post("/breakpoint/remove") {
                    try {
                        val body = parseBreakpointReqBody(call)
                        val result = removeBreakpoint(body.file, body.line, body.breakPointType, body.lambdaOrdinal)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error removing breakpoint", e)
                        call.respond(
                            HttpStatusCode.BadRequest, ApiResponse.error("Failed to remove breakpoint: ${e.message}")
                        )
                    }
                }

                post("/evaluate") {
                    try {
                        val body = call.receive<JsonObject>()
                        val expression = body["expression"]?.jsonPrimitive?.contentOrNull
                            ?: throw IllegalArgumentException("Expression parameter is required")
                        val frameIndex = body["frameIndex"]?.jsonPrimitive?.intOrNull ?: 0
                        val result = evaluateExpression(expression, frameIndex)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        thisLogger().error("Error evaluating expression", e)
                        call.respond(
                            HttpStatusCode.BadRequest, ApiResponse.error("Failed to evaluate expression: ${e.message}")
                        )
                    }
                }

                post("/step/over") {
                    try {
                        val result = debuggerService.stepOver()
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("stepped" to result)))
                    } catch (e: Exception) {
                        thisLogger().error("Error in step over", e)
                        call.respond(
                            HttpStatusCode.InternalServerError, ApiResponse.error("Failed to step over: ${e.message}")
                        )
                    }
                }
                post("/step/into") {
                    try {
                        val result = debuggerService.stepInto()
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("stepped" to result)))
                    } catch (e: Exception) {
                        thisLogger().error("Error in step into", e)
                        call.respond(
                            HttpStatusCode.InternalServerError, ApiResponse.error("Failed to step into: ${e.message}")
                        )
                    }
                }
                post("/step/out") {
                    try {
                        val result = debuggerService.stepOut()
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("stepped" to result)))
                    } catch (e: Exception) {
                        thisLogger().error("Error in step out", e)
                        call.respond(
                            HttpStatusCode.InternalServerError, ApiResponse.error("Failed to step out: ${e.message}")
                        )
                    }
                }
                post("/continueExecution") {
                    try {
                        val result = debuggerService.continueExecution()
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("stepped" to result)))
                    } catch (e: Exception) {
                        thisLogger().error("Error in continueExecution", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse.error("Failed to continueExecution: ${e.message}")
                        )
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

    suspend fun setBreakpoint(
        file: String, line: Int, breakPointType: BreakpointType, lambaOrdinal: Int?
    ): ApiResponse {
        val success = debuggerService.setBreakpoint(file, SourceLine(line), null, breakPointType, lambaOrdinal)
        return ApiResponse.success(success)
    }

    suspend fun removeBreakpoint(
        file: String, line: Int, breakPointType: BreakpointType, lambaOrdinal: Int?
    ): ApiResponse {
        val success = debuggerService.removeBreakpoint(file, SourceLine(line), null, breakPointType, lambaOrdinal)
        return ApiResponse.success(success)
    }

    fun evaluateExpression(expression: String, frameIndex: Int): ApiResponse {
        // Placeholder implementation - expression evaluation not yet implemented
        return ApiResponse.success(
            mapOf(
                "result" to "Expression evaluation not yet implemented", "expression" to expression
            )
        )
    }
}
