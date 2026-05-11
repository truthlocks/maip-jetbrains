/*
 * Copyright 2026 Truthlocks Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.truthlocks.maip.jetbrains.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import kotlinx.coroutines.*

/**
 * Automatic receipt generation service.
 *
 * Subscribes to IDE events (file save, build completion) and generates
 * receipts automatically when the corresponding settings are enabled.
 * VCS commit receipts are handled by [com.truthlocks.maip.jetbrains.listeners.GitCommitListener].
 */
class AutoReceiptService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(AutoReceiptService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initializes the auto-receipt service by registering event listeners.
     */
    fun initialize() {
        val settings = MAIPSettingsState.getInstance()
        if (!settings.isConfigured()) {
            log.info("MAIP not configured, auto-receipt service idle")
            return
        }

        if (settings.autoReceiptOnSave) {
            registerFileSaveListener()
        }

        log.info("Auto-receipt service initialized (save=${settings.autoReceiptOnSave}, build=${settings.autoReceiptOnBuild})")
    }

    /**
     * Registers a listener that creates receipts when files are saved.
     */
    private fun registerFileSaveListener() {
        project.messageBus.connect(this).subscribe(
            FileDocumentManagerListener.TOPIC,
            object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(document: com.intellij.openapi.editor.Document) {
                    val file = FileDocumentManager.getInstance().getFile(document) ?: return
                    if (shouldAutoReceipt(file)) {
                        createAutoReceipt(file, "save")
                    }
                }
            }
        )
    }

    /**
     * Determines whether a file should have an automatic receipt generated.
     * Excludes generated files, binary files, and files outside the project.
     *
     * @param file The virtual file to check.
     * @return `true` if the file qualifies for auto-receipt.
     */
    private fun shouldAutoReceipt(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        if (!file.isInLocalFileSystem) return false
        if (file.extension in EXCLUDED_EXTENSIONS) return false
        if (file.length > MAX_AUTO_RECEIPT_SIZE) return false

        val projectBasePath = project.basePath ?: return false
        return file.path.startsWith(projectBasePath)
    }

    /**
     * Creates an auto-receipt for a file on a background coroutine.
     *
     * @param file   The file to receipt.
     * @param trigger The trigger source (e.g. "save", "build").
     */
    private fun createAutoReceipt(file: VirtualFile, trigger: String) {
        scope.launch {
            try {
                val receiptService = ReceiptService.getInstance(project)
                receiptService.createReceiptForFile(
                    file = file,
                    description = "Auto-receipt ($trigger): ${file.name}"
                )
            } catch (e: Exception) {
                log.warn("Auto-receipt failed for ${file.path}: ${e.message}")
            }
        }
    }

    /**
     * Creates a receipt for a build artifact.
     *
     * @param artifactPath The path to the build artifact.
     * @param buildId      An identifier for the build.
     */
    fun onBuildCompleted(artifactPath: String, buildId: String) {
        val settings = MAIPSettingsState.getInstance()
        if (!settings.autoReceiptOnBuild || !settings.isConfigured()) return

        scope.launch {
            try {
                val client = MAIPProjectService.getInstance(project).getClient() ?: return@launch
                val config = com.truthlocks.maip.jetbrains.client.MAIPConfig.fromSettings()
                if (config.agentId.isBlank()) return@launch

                client.createReceipt(
                    receiptType = "build",
                    description = "Build receipt: $buildId",
                    artifactHash = buildId,
                    agentId = config.agentId,
                    metadata = mapOf(
                        "artifact_path" to artifactPath,
                        "build_id" to buildId,
                        "trigger" to "build"
                    )
                )

                MAIPProjectService.getInstance(project).refreshReceipts()
            } catch (e: Exception) {
                log.warn("Build auto-receipt failed: ${e.message}")
            }
        }
    }

    override fun dispose() {
        scope.cancel("AutoReceiptService disposed")
    }

    companion object {
        /** File extensions excluded from auto-receipts. */
        private val EXCLUDED_EXTENSIONS = setOf(
            "class", "jar", "war", "ear", "so", "dll", "exe",
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg",
            "woff", "woff2", "ttf", "eot",
            "zip", "tar", "gz", "bz2", "7z",
            "lock", "log"
        )

        /** Maximum file size (10 MB) for auto-receipts to avoid hashing large binaries. */
        private const val MAX_AUTO_RECEIPT_SIZE = 10L * 1024 * 1024

        /**
         * Returns the project-level [AutoReceiptService] instance.
         */
        fun getInstance(project: Project): AutoReceiptService {
            return project.getService(AutoReceiptService::class.java)
        }
    }
}
