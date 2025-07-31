package com.github.svk014.autodbgjetbrains.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ToolsResponse(
    val tools: List<DiscoveredEndpoint>,
    val count: Int,
    val generated_at: Long = System.currentTimeMillis()
)

@Serializable
data class DiscoveredEndpoint(
    val name: String,
    val method: String,
    val path: String,
    val description: String,
    val parameters: List<EndpointParameter>,
    val returnType: String
)

@Serializable
data class EndpointParameter(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val tools: List<String> = emptyList()
)

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
    val service: String
)

@Serializable
data class OpenApiResponse(
    val openapi: String,
    val info: ApiInfo,
    val paths: Map<String, Map<String, EndpointSpec>>
)

@Serializable
data class ApiInfo(
    val title: String,
    val version: String,
    val description: String
)

@Serializable
data class EndpointSpec(
    val summary: String,
    val operationId: String,
    val parameters: List<OpenApiParameter>,
    val responses: Map<String, ResponseSpec>
)

@Serializable
data class OpenApiParameter(
    val name: String,
    val `in`: String, // "path" or "query"
    val required: Boolean,
    val description: String,
    val schema: ParameterSchema
)

@Serializable
data class ParameterSchema(
    val type: String
)

@Serializable
data class ResponseSpec(
    val description: String,
    val content: Map<String, ContentSpec>
)

@Serializable
data class ContentSpec(
    val schema: SchemaSpec
)

@Serializable
data class SchemaSpec(
    val type: String
)
