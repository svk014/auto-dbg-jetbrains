package com.github.svk014.autodbgjetbrains.debugger.utils

import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.ui.SimpleTextAttributes
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.Icon

object AsyncDebuggerUtils {

    fun fetchStackFramesAsync(
        executionStack: com.intellij.xdebugger.frame.XExecutionStack
    ): CompletableFuture<List<XStackFrame>> {
        val framesFuture = CompletableFuture<List<XStackFrame>>()
        val allFrames = mutableListOf<XStackFrame>()

        val container = object : com.intellij.xdebugger.frame.XExecutionStack.XStackFrameContainer {
            override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
                try {
                    allFrames.addAll(stackFrames)
                    if (last) {
                        framesFuture.complete(allFrames.toList())
                    }
                } catch (e: Exception) {
                    framesFuture.completeExceptionally(e)
                }
            }

            override fun errorOccurred(errorMessage: String) {
                val exception = RuntimeException("Stack frame computation failed: $errorMessage")
                thisLogger().error(exception)
                framesFuture.completeExceptionally(exception)
            }
        }

        executionStack.computeStackFrames(0, container)
        return framesFuture
    }

    fun fetchFrameVariablesAsync(
        frame: XStackFrame
    ): CompletableFuture<List<Pair<String, XValue>>> {
        val variablesFuture = CompletableFuture<List<Pair<String, XValue>>>()
        val variables = mutableListOf<Pair<String, XValue>>()

        val compositeNode = createVariableCompositeNode(
            onChildrenAdded = { children, last ->
                for (i in 0 until children.size()) {
                    val name = children.getName(i)
                    val xValue = children.getValue(i)
                    variables.add(name to xValue)
                }
                if (last) {
                    variablesFuture.complete(variables.toList())
                }
            },
            onError = { errorMessage ->
                val exception = RuntimeException("Variable computation failed: $errorMessage")
                thisLogger().error(exception)
                variablesFuture.completeExceptionally(exception)
            }
        )

        frame.computeChildren(compositeNode)
        return variablesFuture
    }

    fun fetchValueChildrenAsync(
        xValue: XValue,
        maxChildren: Int = 50
    ): CompletableFuture<List<Pair<String, XValue>>> {
        val childrenFuture = CompletableFuture<List<Pair<String, XValue>>>()
        val children = mutableListOf<Pair<String, XValue>>()

        val compositeNode = createVariableCompositeNode(
            onChildrenAdded = { childrenList, last ->
                val limit = minOf(childrenList.size(), maxChildren)
                for (i in 0 until limit) {
                    val name = childrenList.getName(i)
                    val childValue = childrenList.getValue(i)
                    children.add(name to childValue)
                }
                if (last) {
                    childrenFuture.complete(children.toList())
                }
            },
            onError = { errorMessage ->
                val exception = RuntimeException("Children computation failed: $errorMessage")
                thisLogger().error(exception)
                childrenFuture.completeExceptionally(exception)
            }
        )

        xValue.computeChildren(compositeNode)
        return childrenFuture
    }

    private fun createVariableCompositeNode(
        onChildrenAdded: (XValueChildrenList, Boolean) -> Unit,
        onError: (String) -> Unit
    ): XCompositeNode {
        return object : XCompositeNode {
            override fun addChildren(children: XValueChildrenList, last: Boolean) {
                try {
                    onChildrenAdded(children, last)
                } catch (e: Exception) {
                    thisLogger().error("Error processing children", e)
                    onError("Error processing children: ${e.message}")
                }
            }

            override fun tooManyChildren(remaining: Int) {}
            override fun setAlreadySorted(alreadySorted: Boolean) {}
            override fun setErrorMessage(errorMessage: String) = onError(errorMessage)
            override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) = onError(errorMessage)
            override fun setMessage(message: String, icon: Icon?, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink?) {}
            override fun isObsolete(): Boolean = false
        }
    }

    fun <T> safeGet(future: CompletableFuture<T>, timeoutSeconds: Long, defaultValue: T): T {
        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            thisLogger().warn("Future failed or timed out, returning default value", e)
            defaultValue
        }
    }
}
