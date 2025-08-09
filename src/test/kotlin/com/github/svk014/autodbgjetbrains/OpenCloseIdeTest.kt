package com.github.svk014.autodbgjetbrains

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OpenCloseIdeTest : HeavyPlatformTestCase() {
    private var externalProject: Project? = null

    override fun setUp() {
        super.setUp()
    }

    fun testOpenAndCloseIde() {
        // If we reach this point, the IDE has been opened successfully by setUp()
        assertNotNull(project)
        // Load external Java project
        val projectPath = "/Users/souvikdas/Documents/personal/interview"
        externalProject = ProjectManager.getInstance().loadAndOpenProject(projectPath)
        assertNotNull(externalProject)

        // Now run src/Main.java (default package) in the external project
        val externalProj = externalProject!!
        // Refresh project files
        val projectDir = File(projectPath)
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir)?.refresh(false, true)
        // Get module
        val extModule = ModuleManager.getInstance(externalProj).modules[0]
        // configure a real JDK for the external module
        val jdkHome = System.getenv("JAVA_HOME") ?: throw IllegalStateException("JAVA_HOME must be set for tests")
        val sdk = JavaSdk.getInstance().createJdk("external-jdk", jdkHome)
        ApplicationManager.getApplication().runWriteAction {
            ProjectJdkTable.getInstance().addJdk(sdk)
            ModuleRootManager.getInstance(extModule).modifiableModel.apply {
                this.sdk = sdk
                commit()
            }
        }
        // Create run configuration
        val runManager = RunManager.getInstance(externalProj)
        val configType = ApplicationConfigurationType.getInstance()
        val settings = runManager.createConfiguration("RunMain", configType.configurationFactories[0])
        val appConfig = settings.configuration as ApplicationConfiguration
        appConfig.mainClassName = "actual.random_company1.Main"
        ApplicationManager.getApplication().runWriteAction {
            appConfig.setModule(extModule)
            runManager.addConfiguration(settings)
        }
        // Execute
        val env = ExecutionEnvironmentBuilder
            .create(DefaultRunExecutor.getRunExecutorInstance(), settings)
            .build()

        val output = StringBuilder()
        val descriptorRef = AtomicReference<RunContentDescriptor?>()
        val latch = CountDownLatch(1)

        ProgramRunnerUtil.executeConfigurationAsync(env, false, false)
        { descriptor ->
            descriptorRef.getAndSet(descriptor)
            // attach listener immediately to capture all output
            descriptor.processHandler!!.addProcessListener(object : ProcessListener {
                override fun startNotified(event: ProcessEvent) {}
                override fun processTerminated(event: ProcessEvent) {}
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    output.append(event.text)
                }
            })
            latch.countDown()
        }
        assertTrue("Main.java should run within timeout", latch.await(10, TimeUnit.SECONDS))

        val descriptor = descriptorRef.get()!!
        // wait for process to finish and then print captured output
        descriptor.processHandler!!.waitFor()
        println("Captured stdout:\n$output")

        println("Main.java executed successfully in external project")
    }

    override fun tearDown() {
        try {
            // Dispose of the custom JDK created for the external project
            // This is crucial to prevent the virtual file pointer leak
            ApplicationManager.getApplication().invokeAndWait {
                ApplicationManager.getApplication().runWriteAction {
                    ProjectJdkTable.getInstance().allJdks.firstOrNull { it.name == "external-jdk" }?.let {
                        ProjectJdkTable.getInstance().removeJdk(it)
                    }
                }
            }
        } finally {
            // Close and dispose of the external project first
            externalProject?.let {
                ProjectManager.getInstance().closeAndDispose(it)
            }
            // Then call the super.tearDown() to clean up the main test project
            super.tearDown()
        }
    }
}
