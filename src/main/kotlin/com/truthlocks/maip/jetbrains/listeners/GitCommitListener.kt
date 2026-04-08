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

package com.truthlocks.maip.jetbrains.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.truthlocks.maip.jetbrains.services.ReceiptService
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * VCS checkin handler factory that creates auto-receipt handlers for Git commits.
 *
 * Registered via the `checkinHandlerFactory` extension point and activated only
 * when the Git4Idea plugin is present (via the optional `git-integration.xml`
 * descriptor).
 *
 * When auto-receipt on commit is enabled, the handler fires after a successful
 * commit, hashing the changed files and creating a receipt via the MAIP API.
 */
class GitCommitListener : CheckinHandlerFactory() {

    private val log = Logger.getInstance(GitCommitListener::class.java)

    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return object : CheckinHandler() {

            override fun checkinSuccessful() {
                val settings = MAIPSettingsState.getInstance()
                if (!settings.autoReceiptOnCommit || !settings.isConfigured()) {
                    return
                }

                val project = panel.project
                val commitMessage = panel.commitMessage
                val changedFiles = panel.files.map { it.path }

                if (changedFiles.isEmpty()) return

                val compositeHash = computeCompositeHash(changedFiles)
                val author = settings.agentId.ifBlank { "unknown" }

                log.info("Creating auto-receipt for commit with ${changedFiles.size} files")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val receiptService = ReceiptService.getInstance(project)
                        receiptService.createReceiptForCommit(
                            commitHash = compositeHash,
                            message = commitMessage,
                            author = author,
                            changedFiles = changedFiles
                        )
                    } catch (e: Exception) {
                        log.warn("Failed to create auto-receipt for commit", e)
                    }
                }
            }

            /**
             * Computes a SHA-256 hash of all changed file paths concatenated together.
             * This serves as a deterministic identifier for the changeset when the
             * actual VCS commit hash is not yet available in the checkin handler.
             *
             * @param filePaths The list of changed file paths.
             * @return The hex-encoded SHA-256 hash.
             */
            private fun computeCompositeHash(filePaths: List<String>): String {
                val digest = MessageDigest.getInstance("SHA-256")
                filePaths.sorted().forEach { path ->
                    digest.update(path.toByteArray(Charsets.UTF_8))
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    }
}
