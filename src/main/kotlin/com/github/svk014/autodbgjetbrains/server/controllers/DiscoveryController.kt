package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.services.ApiDiscoveryService
import com.github.svk014.autodbgjetbrains.server.models.*
import com.intellij.openapi.diagnostic.thisLogger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Controller for API discovery endpoints that automatically generates
 * endpoint documentation from annotations
 */
class DiscoveryController {

    private val apiDiscoveryService = ApiDiscoveryService()

    fun configureRoutes(routing: Routing) {
        routing {
            route("/api/discovery") {
                // Discovery endpoint - automatically lists all available API endpoints as tools
                get("/tools") {
                    try {
                        // Discover endpoints from all controller classes
                        val endpoints = apiDiscoveryService.discoverEndpoints(
                            com.github.svk014.autodbgjetbrains.server.controllers.DebuggerController::class
                        )

                        // Convert to proper serializable data classes
                        val discoveredEndpoints = endpoints.map { endpoint ->
                            DiscoveredEndpoint(
                                name = endpoint.name,
                                method = endpoint.method,
                                path = endpoint.path,
                                description = endpoint.description,
                                parameters = endpoint.parameters.map { param ->
                                    EndpointParameter(
                                        name = param.name,
                                        type = param.type,
                                        required = param.required,
                                        description = param.description
                                    )
                                },
                                returnType = endpoint.returnType
                            )
                        }

                        val response = ToolsResponse(
                            tools = discoveredEndpoints,
                            count = endpoints.size
                        )

                        call.respond(response)

                    } catch (e: Exception) {
                        thisLogger().error("Error in /tools discovery endpoint", e)
                        call.respond(ErrorResponse(
                            error = "Failed to discover API endpoints",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }

                // Health check endpoint
                get("/health") {
                    try {
                        call.respond(HealthResponse(
                            status = "healthy",
                            service = "Auto-DBG API Discovery"
                        ))
                    } catch (e: Exception) {
                        thisLogger().error("Error in /health endpoint", e)
                        call.respond(ErrorResponse(
                            error = "Health check failed",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }

                // API schema endpoint for more detailed information
                get("/schema") {
                    try {
                        val endpoints = apiDiscoveryService.discoverEndpoints(
                            com.github.svk014.autodbgjetbrains.server.controllers.DebuggerController::class
                        )

                        val paths = endpoints.associate { endpoint ->
                            endpoint.path to mapOf(
                                endpoint.method.lowercase() to EndpointSpec(
                                    summary = endpoint.description,
                                    operationId = endpoint.name,
                                    parameters = endpoint.parameters.map { param ->
                                        OpenApiParameter(
                                            name = param.name,
                                            `in` = if (endpoint.path.contains("{${param.name}}")) "path" else "query",
                                            required = param.required,
                                            description = param.description,
                                            schema = ParameterSchema(type = param.type)
                                        )
                                    },
                                    responses = mapOf(
                                        "200" to ResponseSpec(
                                            description = "Success",
                                            content = mapOf(
                                                "application/json" to ContentSpec(
                                                    schema = SchemaSpec(type = endpoint.returnType)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        }

                        val response = OpenApiResponse(
                            openapi = "3.0.0",
                            info = ApiInfo(
                                title = "Auto-DBG Debugger API",
                                version = "1.0.0",
                                description = "Automatically generated API documentation for debugger endpoints"
                            ),
                            paths = paths
                        )

                        call.respond(response)

                    } catch (e: Exception) {
                        thisLogger().error("Error in /schema endpoint", e)
                        call.respond(ErrorResponse(
                            error = "Failed to generate API schema",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }
            }
        }
    }
}
