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
import git4idea.repo.GitRepositoryManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import java.nio.file.Path

/**
 * VCS checkin handler factory that creates auto-receipt handlers for Git commits.
 *
 * Registered via the `checkinHandlerFactory` extension point and activated only
 * when the Git4Idea plugin is present (via the optional `git-integration.xml`
 * descriptor).
 *
 * When auto-receipt on commit is enabled, the handler fires after a successful
 * commit, reading the actual Git commit object and creating a receipt via the MAIP API.
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

                log.info("Creating auto-receipt for commit with ${changedFiles.size} files")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val receiptService = ReceiptService.getInstance(project)
                        for (repository in GitRepositoryManager.getInstance(project).repositories) {
                            val root = Path.of(repository.root.path).normalize()
                            val relativeFiles = changedFiles.map { Path.of(it).normalize() }
                                .filter { it.startsWith(root) }
                                .map { root.relativize(it).toString().replace('\\', '/') }
                            if (relativeFiles.isEmpty()) continue
                            val command = GitLineHandler(project, repository.root, GitCommand.LOG)
                            command.addParameters("-1", "--format=%H%x00%an", "HEAD")
                            val result = Git.getInstance().runCommand(command)
                            if (!result.success()) throw IllegalStateException("Cannot read completed Git commit")
                            val fields = result.output.joinToString("\n").split('\u0000', limit = 2)
                            val hash = fields.firstOrNull()?.trim().orEmpty()
                            if (!hash.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}"))) {
                                throw IllegalStateException("Git returned an invalid commit identifier")
                            }
                            receiptService.createReceiptForCommit(
                                commitHash = hash,
                                message = commitMessage,
                                author = fields.getOrElse(1) { "" }.trim(),
                                changedFiles = relativeFiles
                            )
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to create auto-receipt for commit", e)
                    }
                }
            }

        }
    }
}
