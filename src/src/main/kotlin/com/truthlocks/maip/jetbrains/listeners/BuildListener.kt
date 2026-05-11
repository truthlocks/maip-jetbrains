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
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import com.truthlocks.maip.jetbrains.services.AutoReceiptService
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import java.security.MessageDigest
import java.time.Instant

/**
 * Build completion listener that triggers auto-receipt generation
 * when the `autoReceiptOnBuild` setting is enabled.
 *
 * Listens for [ProjectTaskListener.TOPIC] events and creates a build
 * receipt after a successful build, including the project name and
 * a timestamp-based build identifier.
 */
class BuildListener(private val project: Project) : ProjectTaskListener {

    private val log = Logger.getInstance(BuildListener::class.java)

    /**
     * Called when a project build task finishes. If the build is successful
     * and auto-receipt on build is enabled, creates a build receipt.
     *
     * @param result The build task result.
     */
    override fun finished(result: ProjectTaskManager.Result) {
        val settings = MAIPSettingsState.getInstance()
        if (!settings.autoReceiptOnBuild || !settings.isConfigured()) return

        if (result.hasErrors()) {
            log.info("Build completed with errors, skipping auto-receipt")
            return
        }

        val buildId = generateBuildId()
        val artifactPath = project.basePath ?: "unknown"

        log.info("Build completed successfully, creating auto-receipt: $buildId")

        val autoReceiptService = AutoReceiptService.getInstance(project)
        autoReceiptService.onBuildCompleted(
            artifactPath = "$artifactPath/build",
            buildId = buildId
        )
    }

    /**
     * Generates a unique build identifier by hashing the project name
     * and current timestamp.
     *
     * @return A hex-encoded SHA-256 hash suitable for use as a build ID.
     */
    private fun generateBuildId(): String {
        val input = "${project.name}:${Instant.now()}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
