package com.github.svk014.autodbgjetbrains.models

data class ApiRoute(
    val method: String,
    val path: String,
    val params: List<String> = emptyList(),        // path params
    val bodyFields: List<ApiField> = emptyList()   // structured body fields
) {
    companion object {
        fun of(method: String, path: String, bodyFields: List<ApiField> = emptyList()): ApiRoute {
            val regex = "\\{(\\w+)\\}".toRegex()
            val params = regex.findAll(path).map { it.groupValues[1] }.toList()
            return ApiRoute(method, path, params, bodyFields)
        }
    }

    override fun toString(): String = "$method $path"
}

data class ApiField(
    val name: String,
    val type: FieldType = FieldType.STRING,
    val required: Boolean = false,
    val defaultValue: Any? = null
)

enum class FieldType { STRING, NUMBER, BOOLEAN, ANY }