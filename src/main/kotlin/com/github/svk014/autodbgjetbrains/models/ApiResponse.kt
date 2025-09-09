package com.github.svk014.autodbgjetbrains.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {

        fun success(data: JsonElement?): ApiResponse = ApiResponse(success = true, data = data)

        inline fun <reified T> success(data: T): ApiResponse = ApiResponse(success = true, data = Json.encodeToJsonElement(kotlinx.serialization.serializer<T>(), data))

        // This works because reified generics preserve type information at runtime
        fun error(message: String): ApiResponse = ApiResponse(success = false, error = message)
    }
}
