package com.github.svk014.autodbgjetbrains.annotations

/**
 * Annotation to mark API endpoint methods and provide metadata for automatic discovery
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiEndpoint(
    val name: String,
    val method: String,
    val path: String,
    val description: String,
    val returnType: String = "Any"
)

/**
 * Annotation to mark API endpoint parameters
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiParam(
    val description: String,
    val required: Boolean = true,
    val type: String = ""
)
