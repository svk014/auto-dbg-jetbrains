package com.github.svk014.autodbgjetbrains.models

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
