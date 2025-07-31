package com.github.svk014.autodbgjetbrains.debugger.factory

import com.github.svk014.autodbgjetbrains.debugger.interfaces.CallStackRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.FrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.interfaces.VariableRetriever
import com.github.svk014.autodbgjetbrains.debugger.java.JavaCallStackRetriever
import com.github.svk014.autodbgjetbrains.debugger.java.JavaFrameRetriever
import com.github.svk014.autodbgjetbrains.debugger.java.JavaVariableRetriever
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager

/**
 * Factory for creating language-specific debugger components
 */
class DebuggerComponentFactory {

    enum class Language {
        JAVA,
        JAVASCRIPT,
        PYTHON,
        UNKNOWN
    }

    companion object {
        /**
         * Detect the programming language based on debug process class name
         */
        fun detectLanguage(processClassName: String): Language {
            return when {
                processClassName.contains("java", ignoreCase = true) ||
                processClassName.contains("DebugProcessImpl") -> Language.JAVA
                processClassName.contains("javascript", ignoreCase = true) ||
                processClassName.contains("node", ignoreCase = true) -> Language.JAVASCRIPT
                processClassName.contains("python", ignoreCase = true) -> Language.PYTHON
                else -> Language.UNKNOWN
            }
        }

        /**
         * Create frame retriever for the specified language
         */
        fun createFrameRetriever(language: Language, project: Project): FrameRetriever? {
            return when (language) {
                Language.JAVA -> JavaFrameRetriever(project)
                Language.JAVASCRIPT -> {
                    // TODO: Implement JavaScript frame retriever
                    null
                }
                Language.PYTHON -> {
                    // TODO: Implement Python frame retriever
                    null
                }
                Language.UNKNOWN -> null
            }
        }

        /**
         * Create call stack retriever for the specified language
         */
        fun createCallStackRetriever(language: Language, project: Project): CallStackRetriever? {
            return when (language) {
                Language.JAVA -> JavaCallStackRetriever(project)
                Language.JAVASCRIPT -> {
                    // TODO: Implement JavaScript call stack retriever
                    null
                }
                Language.PYTHON -> {
                    // TODO: Implement Python call stack retriever
                    null
                }
                Language.UNKNOWN -> null
            }
        }

        /**
         * Create variable retriever for the specified language
         */
        fun createVariableRetriever(language: Language, project: Project): VariableRetriever? {
            return when (language) {
                Language.JAVA -> JavaVariableRetriever(project)
                Language.JAVASCRIPT -> {
                    // TODO: Implement JavaScript variable retriever
                    null
                }
                Language.PYTHON -> {
                    // TODO: Implement Python variable retriever
                    null
                }
                Language.UNKNOWN -> null
            }
        }

        /**
         * Check if there's an active debug session
         */
        private fun hasActiveDebugSession(project: Project): Boolean {
            return try {
                val debuggerManager = XDebuggerManager.getInstance(project)
                debuggerManager.currentSession != null
            } catch (e: Exception) {
                false
            }
        }
    }
}
