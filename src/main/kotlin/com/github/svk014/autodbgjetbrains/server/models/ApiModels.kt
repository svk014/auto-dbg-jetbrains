package com.github.svk014.autodbgjetbrains.server.models

import kotlinx.serialization.Serializable

/**
 * Response wrapper for API endpoints
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * API endpoint definition for MCP server discovery
 */
@Serializable
data class ApiEndpoint(
    val name: String,
    val method: String,
    val path: String,
    val description: String,
    val parameters: List<ApiParameter>,
    val returnType: String
)

/**
 * API parameter definition
 */
@Serializable
data class ApiParameter(
    val name: String,
    val type: String,
    val required: Boolean,
    val defaultValue: String? = null,
    val description: String
)


data class ApiEndpointInfo(
    val name: String,
    val method: String,
    val path: String,
    val description: String,
    val parameters: List<ApiParameterInfo>,
    val returnType: String
)

data class ApiParameterInfo(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

