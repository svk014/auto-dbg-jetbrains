package com.github.svk014.autodbgjetbrains.server.controllers

import com.github.svk014.autodbgjetbrains.services.ApiDiscoveryService
import com.github.svk014.autodbgjetbrains.server.controllers.DebuggerController
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.reflect.full.createInstance

/**
 * Controller for API discovery endpoints that automatically generates
 * endpoint documentation from annotations
 */
class DiscoveryController {

    private val apiDiscoveryService = ApiDiscoveryService()

    fun configureRoutes(routing: Routing) {
        routing {
            // Discovery endpoint - automatically lists all available API endpoints as tools
            get("/tools") {
                try {
                    // Discover endpoints from all controller classes
                    val endpoints = apiDiscoveryService.discoverEndpoints(
                        DebuggerController::class
                        // Add other controller classes here as they are created
                    )

                    // Convert to the format expected by the client
                    val toolsResponse = endpoints.map { endpoint ->
                        mapOf(
                            "name" to endpoint.name,
                            "method" to endpoint.method,
                            "path" to endpoint.path,
                            "description" to endpoint.description,
                            "parameters" to endpoint.parameters.map { param ->
                                mapOf(
                                    "name" to param.name,
                                    "type" to param.type,
                                    "required" to param.required,
                                    "description" to param.description
                                )
                            },
                            "returnType" to endpoint.returnType
                        )
                    }

                    call.respond(mapOf(
                        "tools" to toolsResponse,
                        "count" to endpoints.size,
                        "generated_at" to System.currentTimeMillis()
                    ))

                } catch (e: Exception) {
                    call.respond(mapOf(
                        "error" to "Failed to discover API endpoints",
                        "message" to (e.message ?: "Unknown error"),
                        "tools" to emptyList<Any>()
                    ))
                }
            }

            // Health check endpoint
            get("/health") {
                call.respond(mapOf(
                    "status" to "healthy",
                    "timestamp" to System.currentTimeMillis(),
                    "service" to "Auto-DBG API Discovery"
                ))
            }

            // API schema endpoint for more detailed information
            get("/schema") {
                try {
                    val endpoints = apiDiscoveryService.discoverEndpoints(
                        DebuggerController::class
                    )

                    call.respond(mapOf(
                        "openapi" to "3.0.0",
                        "info" to mapOf(
                            "title" to "Auto-DBG Debugger API",
                            "version" to "1.0.0",
                            "description" to "Automatically generated API documentation for debugger endpoints"
                        ),
                        "paths" to endpoints.associate { endpoint ->
                            endpoint.path to mapOf(
                                endpoint.method.lowercase() to mapOf(
                                    "summary" to endpoint.description,
                                    "operationId" to endpoint.name,
                                    "parameters" to endpoint.parameters.map { param ->
                                        mapOf(
                                            "name" to param.name,
                                            "in" to if (endpoint.path.contains("{${param.name}}")) "path" else "query",
                                            "required" to param.required,
                                            "description" to param.description,
                                            "schema" to mapOf("type" to param.type)
                                        )
                                    },
                                    "responses" to mapOf(
                                        "200" to mapOf(
                                            "description" to "Success",
                                            "content" to mapOf(
                                                "application/json" to mapOf(
                                                    "schema" to mapOf("type" to endpoint.returnType)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        }
                    ))

                } catch (e: Exception) {
                    call.respond(mapOf(
                        "error" to "Failed to generate API schema",
                        "message" to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }
}
