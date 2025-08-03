package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.VariableRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.Variable
import com.github.svk014.autodbgjetbrains.debugger.utils.AsyncDebuggerUtils
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.sun.jdi.ArrayReference
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.Icon
import kotlin.math.min

class JavaVariableRetriever(private val project: Project) : VariableRetriever {

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 3L
        private const val FRAME_FETCH_TIMEOUT_SECONDS = 5L
        private const val MAX_ARRAY_PREVIEW_SIZE = 100
        private const val RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 100L

        /**
         * The `ACC_SYNTHETIC` flag from the JVM specification, used to identify synthetic members.
         * The value is 0x1000, as defined in `java.lang.reflect.Modifier` and the JVM spec.
         * It is defined here because `Modifier.SYNTHETIC` is package-private and inaccessible.
         */
        private const val ACC_SYNTHETIC = 0x1000
    }

    override fun getFrameVariables(frameId: String, maxDepth: Int): Map<String, Variable> {
        return try {
            val debuggerManager = XDebuggerManager.getInstance(project)
            val currentSession = debuggerManager.currentSession ?: return emptyMap()

            if (!currentSession.isSuspended) return emptyMap()

            val suspendContext = currentSession.suspendContext ?: return emptyMap()
            val activeExecutionStack = suspendContext.activeExecutionStack ?: return emptyMap()

            val depth = frameId.toIntOrNull() ?: 0

            val framesFuture = AsyncDebuggerUtils.fetchStackFramesAsync(activeExecutionStack)
            val frames = AsyncDebuggerUtils.safeGet(framesFuture, 5, emptyList())

            if (depth >= frames.size) return emptyMap()

            val targetFrame = frames[depth]
            extractVariablesFromFrame(targetFrame, maxDepth)
        } catch (ex: Exception) {
            thisLogger().error("Error getting frame variables for frameId: $frameId", ex)
            emptyMap()
        }
    }

    private fun extractVariablesFromFrame(frame: XStackFrame, maxDepth: Int): Map<String, Variable> {
        return try {
            val variablesFuture = AsyncDebuggerUtils.fetchFrameVariablesAsync(frame)
            val rawVariables = AsyncDebuggerUtils.safeGet(variablesFuture, FRAME_FETCH_TIMEOUT_SECONDS, emptyList())

            rawVariables.associate { (name, xValue) ->
                name.let { it to convertXValueToVariable(it, xValue, 0, maxDepth) }
            }
        } catch (ex: Exception) {
            thisLogger().error("Error extracting variables from frame", ex)
            mapOf(
                "error" to Variable(
                    name = "error",
                    value = "Failed to extract variables: ${ex.message}",
                    type = "Error",
                    isError = true
                )
            )
        }
    }

    private fun convertXValueToVariable(
        name: String,
        xValue: XValue,
        currentDepth: Int,
        maxDepth: Int
    ): Variable {
        return try {
            withRetry {
                extractCompleteVariableInfo(name, xValue, currentDepth, maxDepth)
            }
        } catch (e: Exception) {
            createErrorVariable(name, e)
        }
    }

    private fun extractCompleteVariableInfo(
        name: String,
        xValue: XValue,
        currentDepth: Int,
        maxDepth: Int
    ): Variable {
        val future = CompletableFuture<Variable>()
        val handler = createPresentationHandler(name, currentDepth, maxDepth, xValue, future)

        xValue.computePresentation(handler, XValuePlace.TREE)

        return try {
            future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            thisLogger().error(e)
            throw VariableExtractionError.TimeoutError("Timeout extracting value for '$name'")
        } catch (e: Exception) {
            createErrorVariable(name, e)
        }
    }

    private fun createPresentationHandler(
        name: String,
        currentDepth: Int,
        maxDepth: Int,
        xValue: XValue,
        future: CompletableFuture<Variable>
    ): XValueNode {
        return object : XValueNode {
            override fun setPresentation(icon: Icon?, type: String?, value: String, hasChildren: Boolean) {
                handlePresentation(name, type, value, hasChildren, xValue, currentDepth, maxDepth, future)
            }

            override fun setPresentation(icon: Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
                val valueBuilder = StringBuilder()
                presentation.renderValue(object : XValuePresentation.XValueTextRenderer {
                    override fun renderValue(value: String) {
                        valueBuilder.append(value)
                    }

                    override fun renderValue(value: String, attributes: TextAttributesKey) {
                        valueBuilder.append(value)
                    }

                    override fun renderStringValue(value: String) {
                        valueBuilder.append(value)
                    }

                    override fun renderStringValue(
                        value: String,
                        prefix: String?,
                        maxLength: Int
                    ) {
                        valueBuilder.append(value)
                    }

                    override fun renderNumericValue(value: String) {
                        valueBuilder.append(value)
                    }

                    override fun renderKeywordValue(value: String) {
                        valueBuilder.append(value)
                    }

                    override fun renderComment(value: String) {
                        valueBuilder.append(value)
                    }

                    override fun renderSpecialSymbol(value: String) {
                        valueBuilder.append(value)
                    }

                    override fun renderError(value: String) {
                        valueBuilder.append(value)
                    }
                })

                handlePresentation(
                    name,
                    presentation.type,
                    presentation.separator + valueBuilder.toString(),
                    hasChildren,
                    xValue,
                    currentDepth,
                    maxDepth,
                    future
                )
            }

            override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
                future.complete(
                    Variable(
                        name = name,
                        value = "<Large value available>",
                        type = "LargeValue",
                        hasChildren = true
                    )
                )
            }
        }
    }

    private fun handlePresentation(
        name: String,
        type: String?,
        value: String,
        hasChildren: Boolean,
        xValue: XValue,
        currentDepth: Int,
        maxDepth: Int,
        future: CompletableFuture<Variable>
    ) {
        try {
            val variable = buildVariable(name, type, value, hasChildren, xValue, currentDepth, maxDepth)
            future.complete(variable)
        } catch (e: Exception) {
            future.complete(createErrorVariable(name, e))
        }
    }

    private fun buildVariable(
        name: String,
        type: String?,
        value: String,
        hasChildren: Boolean,
        xValue: XValue,
        currentDepth: Int,
        maxDepth: Int
    ): Variable {
        val objectId = extractObjectId(xValue, value)
        val isNull = value == "null"
        val modifiers = extractModifiers(xValue)
        val typeInfo = extractFullTypeInfo(xValue)
        val (arraySize, arrayElements) = extractArrayInfo(xValue)
        val collectionSize = if (arraySize == null) extractCollectionSize(xValue) else null

        return Variable(
            name = name,
            value = value,
            type = type ?: typeInfo.rawType,
            fullyQualifiedType = typeInfo.rawType,
            genericType = typeInfo.genericSignature,
            isNull = isNull,
            hasChildren = hasChildren,
            objectId = objectId,
            modifiers = modifiers.accessModifiers,
            isStatic = modifiers.isStatic,
            isFinal = modifiers.isFinal,
            isEnum = typeInfo.isEnum,
            isInterface = typeInfo.isInterface,
            isLambda = typeInfo.isLambda,
            arraySize = arraySize,
            collectionSize = collectionSize,
            children = if (hasChildren && currentDepth < maxDepth) {
                arrayElements?.associateBy { it.name } ?: extractChildrenVariables(xValue, currentDepth, maxDepth)
            } else {
                emptyMap()
            }
        )
    }

    private fun createErrorVariable(name: String, e: Throwable): Variable {
        return Variable(
            name = name,
            value = "Error: ${e.message ?: e.javaClass.simpleName}",
            type = "Error",
            isError = true
        )
    }

    private fun <T> withRetry(
        attempts: Int = RETRY_ATTEMPTS,
        delayMs: Long = RETRY_DELAY_MS,
        operation: () -> T
    ): T {
        repeat(attempts - 1) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                if (e is VariableExtractionError.DebuggerNotAvailable) throw e
                Thread.sleep(delayMs * (attempt + 1))
            }
        }
        return operation()
    }

    private fun extractFullTypeInfo(xValue: XValue): TypeInfo {
        try {
            if (xValue !is JavaValue) return TypeInfo("Unknown")
            val jdiType = xValue.descriptor.type ?: return TypeInfo("Unknown")

            val isInterface = jdiType is com.sun.jdi.InterfaceType
            val isEnum = (jdiType as? com.sun.jdi.ClassType)?.isEnum ?: false
            val genericSignature = (jdiType as? com.sun.jdi.ReferenceType)?.genericSignature()
            val modifiers = (jdiType as? com.sun.jdi.ReferenceType)?.modifiers() ?: 0
            val isSynthetic = (modifiers and ACC_SYNTHETIC) != 0

            return TypeInfo(
                rawType = jdiType.name(),
                genericSignature = genericSignature,
                isInterface = isInterface,
                isEnum = isEnum,
                isLambda = isSynthetic && jdiType.name().contains("$\$Lambda$")
            )
        } catch (e: Exception) {
            thisLogger().error("Failed to extract full type info", e)
            return TypeInfo("Unknown")
        }
    }

    private fun extractArrayInfo(xValue: XValue): Pair<Int?, List<Variable>?> {
        try {
            if (xValue !is JavaValue) return null to null

            val value = xValue.descriptor.calcValue(xValue.evaluationContext)
            if (value !is ArrayReference) return null to null

            val length = value.length()
            val elements = if (length in 1..MAX_ARRAY_PREVIEW_SIZE) {
                value.getValues(0, min(length, MAX_ARRAY_PREVIEW_SIZE))
                    .mapIndexed { index, element ->
                        Variable(
                            name = "[$index]",
                            value = element?.toString() ?: "null",
                            type = element?.type()?.name() ?: "Object"
                        )
                    }
            } else {
                null
            }
            return length to elements
        } catch (e: Exception) {
            thisLogger().warn("Failed to extract array info", e)
            return null to null
        }
    }

    private fun extractCollectionSize(xValue: XValue): Int? {
        try {
            if (xValue !is JavaValue) return null

            val value = xValue.descriptor.calcValue(xValue.evaluationContext)
            if (value !is ObjectReference) return null

            val refType = value.referenceType()
            val sizeMethod = refType.methodsByName("size", "()I").firstOrNull { it.argumentTypes().isEmpty() }
                ?: return null

            val thread = xValue.evaluationContext.suspendContext.thread?.threadReference
            val result = thread?.let { value.invokeMethod(it, sizeMethod, emptyList(), 0) }
            return (result as? IntegerValue)?.value()
        } catch (e: Exception) {
            thisLogger().warn("Failed to extract collection size", e)
            return null
        }
    }

    private data class VariableModifiers(
        val accessModifiers: List<String>,
        val isStatic: Boolean,
        val isFinal: Boolean
    )

    private fun extractModifiers(xValue: XValue): VariableModifiers {
        try {
            if (xValue !is JavaValue) {
                return VariableModifiers(listOf("unknown"), isStatic = false, isFinal = false)
            }

            return when (val descriptor = xValue.descriptor) {
                is FieldDescriptorImpl -> {
                    val field = descriptor.field
                    val modifiers = mutableListOf<String>()
                    if (field.isPublic) modifiers.add("public")
                    else if (field.isPrivate) modifiers.add("private")
                    else if (field.isProtected) modifiers.add("protected")
                    else modifiers.add("package-private")
                    VariableModifiers(
                        accessModifiers = modifiers,
                        isStatic = field.isStatic,
                        isFinal = field.isFinal
                    )
                }

                is LocalVariableDescriptorImpl -> {
                    VariableModifiers(
                        accessModifiers = listOf("local"),
                        isStatic = false,
                        isFinal = false
                    )
                }

                else -> VariableModifiers(listOf("unknown"), isStatic = false, isFinal = false)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to extract modifiers", e)
            return VariableModifiers(listOf("unknown"), isStatic = false, isFinal = false)
        }
    }

    private fun extractObjectId(xValue: XValue, presentationValue: String?): String? {
        try {
            if (xValue !is JavaValue) {
                return extractObjectIdFromPresentation(presentationValue)
            }
            val evaluationResult = xValue.descriptor.calcValue(xValue.evaluationContext)
            if (evaluationResult is ObjectReference) {
                return evaluationResult.uniqueID().toString()
            }
            return extractObjectIdFromPresentation(presentationValue)
        } catch (e: Exception) {
            thisLogger().warn("Failed to extract object ID", e)
            return extractObjectIdFromPresentation(presentationValue)
        }
    }

    private fun extractObjectIdFromPresentation(presentationValue: String?): String? {
        return presentationValue?.let { value ->
            val atIndex = value.lastIndexOf('@')
            if (atIndex != -1 && atIndex < value.length - 1) {
                val idPart = value.substring(atIndex + 1).split(' ')[0]
                if (idPart.matches(Regex("[0-9a-fA-F]+"))) {
                    idPart
                } else null
            } else null
        }
    }

    private fun extractChildrenVariables(
        xValue: XValue,
        currentDepth: Int,
        maxDepth: Int
    ): Map<String, Variable> {
        try {
            val childrenFuture = AsyncDebuggerUtils.fetchValueChildrenAsync(xValue)
            val rawChildren = AsyncDebuggerUtils.safeGet(childrenFuture, DEFAULT_TIMEOUT_SECONDS, emptyList())

            return rawChildren.associate { (childName, childValue) ->
                childName.let {
                    it to convertXValueToVariable(
                        it,
                        childValue,
                        currentDepth + 1,
                        maxDepth
                    )
                }
            }
        } catch (ex: Exception) {
            thisLogger().error("Error extracting children variables", ex)
            return mapOf(
                "error" to Variable(
                    name = "error",
                    value = "Failed to extract children: ${ex.message}",
                    type = "Error",
                    isError = true
                )
            )
        }
    }
}
