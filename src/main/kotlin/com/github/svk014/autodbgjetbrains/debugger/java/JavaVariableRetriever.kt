package com.github.svk014.autodbgjetbrains.debugger.java

import com.github.svk014.autodbgjetbrains.debugger.interfaces.VariableRetriever
import com.github.svk014.autodbgjetbrains.debugger.models.Variable
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame

/**
 * Java-specific implementation for retrieving variable information
 */
class JavaVariableRetriever(private val project: Project) : VariableRetriever {

    override fun getFrameVariables(frameId: String, maxDepth: Int): Map<String, Variable> {
        return try {
            val debuggerManager = XDebuggerManager.getInstance(project)
            val currentSession = debuggerManager.currentSession ?: return emptyMap()

            if (!currentSession.isSuspended) return emptyMap()

            val suspendContext = currentSession.suspendContext ?: return emptyMap()
            val activeExecutionStack = suspendContext.activeExecutionStack ?: return emptyMap()

            // Parse frameId to get frame depth
            val depth = frameId.toIntOrNull() ?: 0

            // Get the stack frames
            val frames = mutableListOf<XStackFrame>()
            activeExecutionStack.computeStackFrames(0, object : com.intellij.xdebugger.frame.XExecutionStack.XStackFrameContainer {
                override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
                    frames.addAll(stackFrames)
                }

                override fun errorOccurred(errorMessage: String) {
                    // Handle error - frames will remain empty
                }
            })

            if (depth >= frames.size) return emptyMap()

            val targetFrame = frames[depth]
            val variables = mutableMapOf<String, Variable>()

            // Get variables from the target frame
            extractVariablesFromFrame(targetFrame, variables, maxDepth)

            variables
        } catch (ex: Exception) {
            emptyMap()
        }
    }

    /**
     * Extract variables from a stack frame using XDebugger APIs
     */
    private fun extractVariablesFromFrame(
        frame: XStackFrame,
        variables: MutableMap<String, Variable>,
        maxDepth: Int
    ) {
        try {
            // Create a composite node to collect variables
            val variableNode = object : com.intellij.xdebugger.frame.XCompositeNode {
                override fun addChildren(children: com.intellij.xdebugger.frame.XValueChildrenList, last: Boolean) {
                    for (i in 0 until children.size()) {
                        val name = children.getName(i)
                        val xValue = children.getValue(i)
                        val variable = convertXValueToVariable(name, xValue, 0, maxDepth)
                        variables[name] = variable
                    }
                }

                override fun tooManyChildren(remaining: Int) {
                    variables["..."] = Variable(
                        name = "...",
                        value = "$remaining more variables",
                        type = "Info"
                    )
                }

                override fun setAlreadySorted(alreadySorted: Boolean) {
                    // No action needed
                }

                override fun setErrorMessage(errorMessage: String) {
                    variables["error"] = Variable(
                        name = "error",
                        value = errorMessage,
                        type = "Error"
                    )
                }

                override fun setErrorMessage(errorMessage: String, link: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?) {
                    variables["error"] = Variable(
                        name = "error",
                        value = errorMessage,
                        type = "Error"
                    )
                }

                override fun setMessage(message: String, icon: javax.swing.Icon?, attributes: com.intellij.ui.SimpleTextAttributes, link: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?) {
                    variables["message"] = Variable(
                        name = "message",
                        value = message,
                        type = "Info"
                    )
                }

                override fun isObsolete(): Boolean = false
            }

            // Compute children (variables) of the frame
            frame.computeChildren(variableNode)

        } catch (ex: Exception) {
            // If we can't extract variables normally, try alternative approach
            extractVariablesFromFrameString(frame, variables)
        }
    }

    /**
     * Convert XValue to our Variable model
     */
    private fun convertXValueToVariable(
        name: String,
        xValue: com.intellij.xdebugger.frame.XValue,
        currentDepth: Int,
        maxDepth: Int
    ): Variable {
        return try {
            // Get string representation of the value
            val valueText = xValue.toString()

            // If we've reached max depth, return simple variable
            if (currentDepth >= maxDepth) {
                return Variable(
                    name = name,
                    value = valueText,
                    type = extractTypeFromXValue(xValue)
                )
            }

            // For complex objects, try to get children
            val children = mutableMapOf<String, Variable>()

            try {
                val childrenNode = object : com.intellij.xdebugger.frame.XCompositeNode {
                    override fun addChildren(childrenList: com.intellij.xdebugger.frame.XValueChildrenList, last: Boolean) {
                        for (i in 0 until minOf(childrenList.size(), 10)) { // Limit to avoid infinite recursion
                            val childName = childrenList.getName(i)
                            val childValue = childrenList.getValue(i)
                            children[childName] = convertXValueToVariable(
                                childName,
                                childValue,
                                currentDepth + 1,
                                maxDepth
                            )
                        }
                    }

                    override fun tooManyChildren(remaining: Int) {
                        children["..."] = Variable(
                            name = "...",
                            value = "$remaining more items",
                            type = "Info"
                        )
                    }

                    override fun setAlreadySorted(alreadySorted: Boolean) {}

                    override fun setErrorMessage(errorMessage: String) {
                        children["error"] = Variable(
                            name = "error",
                            value = errorMessage,
                            type = "Error"
                        )
                    }

                    override fun setErrorMessage(errorMessage: String, link: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?) {
                        children["error"] = Variable(
                            name = "error",
                            value = errorMessage,
                            type = "Error"
                        )
                    }

                    override fun setMessage(message: String, icon: javax.swing.Icon?, attributes: com.intellij.ui.SimpleTextAttributes, link: com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink?) {
                        children["message"] = Variable(
                            name = "message",
                            value = message,
                            type = "Info"
                        )
                    }

                    override fun isObsolete(): Boolean = false
                }

                xValue.computeChildren(childrenNode)
            } catch (ex: Exception) {
                // Children extraction failed, proceed without children
            }

            Variable(
                name = name,
                value = valueText,
                type = extractTypeFromXValue(xValue),
                children = children
            )
        } catch (ex: Exception) {
            Variable(
                name = name,
                value = "Error: ${ex.message}",
                type = "Error"
            )
        }
    }

    /**
     * Extract type information from XValue
     */
    private fun extractTypeFromXValue(xValue: com.intellij.xdebugger.frame.XValue): String {
        return try {
            // Try to get type from the XValue's string representation
            val valueString = xValue.toString()
            when {
                valueString.contains("String") -> "String"
                valueString.contains("Integer") -> "Integer"
                valueString.contains("Boolean") -> "Boolean"
                valueString.contains("Double") -> "Double"
                valueString.contains("Float") -> "Float"
                valueString.contains("Long") -> "Long"
                valueString.contains("Array") -> "Array"
                valueString.contains("List") -> "List"
                valueString.contains("Map") -> "Map"
                else -> "Object"
            }
        } catch (ex: Exception) {
            "Unknown"
        }
    }

    /**
     * Fallback method to extract variables from frame string representation
     */
    private fun extractVariablesFromFrameString(
        frame: XStackFrame,
        variables: MutableMap<String, Variable>
    ) {
        try {
            val frameString = frame.toString()

            // Try to parse basic variable info from frame string
            // This is a fallback and may not work for all cases
            variables["frameInfo"] = Variable(
                name = "frameInfo",
                value = frameString,
                type = "Debug Info"
            )
        } catch (ex: Exception) {
            // Even fallback failed, add minimal info
            variables["error"] = Variable(
                name = "error",
                value = "Unable to extract variables: ${ex.message}",
                type = "Error"
            )
        }
    }
}
