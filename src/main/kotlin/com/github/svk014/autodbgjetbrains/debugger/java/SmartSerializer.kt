package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.models.*
import com.intellij.debugger.engine.JavaValue
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.xdebugger.frame.XValue
import com.sun.jdi.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation

object SmartSerializer {

    private const val MAX_DEPTH = 3

    fun serializeVariables(variables: List<JavaValue>): List<SerializedVariable> {
        val visitedObjects = mutableSetOf<ObjectReference>()

        return variables.map { variable ->
            try {
                val jdiValue = variable.descriptor.calcValue(variable.evaluationContext)
                val serializedValue = serializeValue(jdiValue, visitedObjects, 0)
                SerializedVariable(variable.name, serializedValue)
            } catch (e: Exception) {
                thisLogger().error("Error serializing variable: ${variable.name}", e)
                SerializedVariable(variable.name, ObjectSummary("Error", "Failed to serialize: ${e.message}"))
            }
        }
    }

    suspend fun serializeValue(xValue: XValue): LlmVariableValue = suspendCoroutine { continuation ->
        if (xValue is JavaValue) {
            val jdiValue = xValue.descriptor.calcValue(xValue.evaluationContext)
            val visitedObjects = mutableSetOf<ObjectReference>()
            val serialized = serializeValue(jdiValue, visitedObjects, 0)
            continuation.resume(serialized)
        } else {
            // Fallback for non-Java values
            val xValueNode = object : XValueNode {
                override fun setPresentation(icon: javax.swing.Icon?, type: String?, value: String, hasChildren: Boolean) {
                    val result = BasicValue(type ?: "Unknown", value)
                    continuation.resume(result)
                }

                override fun setPresentation(icon: javax.swing.Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
                    val result = ObjectSummary(presentation.type ?: "Unknown", presentation.toString())
                    continuation.resume(result)
                }

                override fun setFullValueEvaluator(fullValueEvaluator: com.intellij.xdebugger.frame.XFullValueEvaluator) {}
            }
            xValue.computePresentation(xValueNode, XValuePlace.TREE)
        }
    }

    private fun serializeValue(
        value: Value?,
        visitedObjects: MutableSet<ObjectReference>,
        depth: Int
    ): LlmVariableValue {
        if (value == null) {
            return BasicValue("null", null)
        }

        if (value is PrimitiveValue) {
            val primitiveValue = when (value) {
                is BooleanValue -> value.value().toString()
                is CharValue -> "'${value.value()}'"
                else -> value.toString()
            }

            return BasicValue(value.type().name(), primitiveValue)
        }

        return when (value) {
            is StringReference -> BasicValue("String", value.value())

            is ArrayReference -> {
                val firstElements = value.values.take(5).map { it?.toString() ?: "null" }
                ArraySummary(
                    objectType = value.type().name(),
                    size = value.length(),
                    firstElements = firstElements
                )
            }

            is ObjectReference -> {
                if (visitedObjects.contains(value)) {
                    return ObjectSummary(value.referenceType().name(), "[Circular Reference]")
                }

                if (depth >= MAX_DEPTH) {
                    return ObjectSummary(value.referenceType().name(), "[Max Depth Reached]")
                }

                visitedObjects.add(value)

                val fields = value.referenceType().allFields().take(5).associate { field ->
                    field.name() to serializeValue(
                        value.getValue(field),
                        visitedObjects,
                        depth + 1
                    )
                }

                ObjectFields(value.referenceType().name(), fields)
            }

            else -> ObjectSummary(value.type().name(), "[Unsupported Type]")
        }
    }
}