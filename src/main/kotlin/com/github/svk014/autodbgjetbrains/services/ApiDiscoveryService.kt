package com.github.svk014.autodbgjetbrains.services

import com.github.svk014.autodbgjetbrains.annotations.ApiEndpoint
import com.github.svk014.autodbgjetbrains.annotations.ApiParam
import com.github.svk014.autodbgjetbrains.models.ApiEndpointInfo
import com.github.svk014.autodbgjetbrains.models.ApiParameterInfo
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters

/**
 * Service to automatically discover API endpoints using annotations
 */
class ApiDiscoveryService {

    /**
     * Scans a controller class and extracts all annotated API endpoints
     */
    fun discoverEndpoints(controllerClass: KClass<*>): List<ApiEndpointInfo> {
        return controllerClass.declaredMemberFunctions
            .mapNotNull { function ->
                function.findAnnotation<ApiEndpoint>()?.let { annotation ->
                    createEndpointInfo(function, annotation)
                }
            }
    }

    /**
     * Scans multiple controller classes and returns all discovered endpoints
     */
    fun discoverEndpoints(vararg controllerClasses: KClass<*>): List<ApiEndpointInfo> {
        return controllerClasses.flatMap { discoverEndpoints(it) }
    }

    private fun createEndpointInfo(function: KFunction<*>, annotation: ApiEndpoint): ApiEndpointInfo {
        val parameters = function.valueParameters.map { param ->
            val apiParam = param.findAnnotation<ApiParam>()
            ApiParameterInfo(
                name = param.name ?: "unknown",
                type = apiParam?.type?.takeIf { it.isNotEmpty() } ?: param.type.toString(),
                required = apiParam?.required ?: true,
                description = apiParam?.description ?: "No description available"
            )
        }

        return ApiEndpointInfo(
            name = annotation.name,
            method = annotation.method,
            path = annotation.path,
            description = annotation.description,
            parameters = parameters,
            returnType = annotation.returnType
        )
    }
}
