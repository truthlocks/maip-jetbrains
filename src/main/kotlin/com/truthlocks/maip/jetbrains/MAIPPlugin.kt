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

package com.truthlocks.maip.jetbrains

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.truthlocks.maip.jetbrains.services.AutoReceiptService
import com.truthlocks.maip.jetbrains.services.MAIPProjectService
import com.truthlocks.maip.jetbrains.services.RegistrationService

/**
 * Entry point for the MAIP plugin. Executes on project open to initialize
 * project-level services and register event listeners.
 *
 * This [ProjectActivity] runs on a background thread, so it is safe to
 * perform network calls and other long-running initialization here.
 */
class MAIPPlugin : ProjectActivity {

    private val log = Logger.getInstance(MAIPPlugin::class.java)

    /**
     * Called when a project is opened. Initializes the MAIP project service
     * and the auto-receipt service so they begin caching data and listening
     * for events.
     *
     * Also checks for stored credentials; if none are found, shows a balloon
     * notification prompting the user to register.
     *
     * @param project The opened project.
     */
    override suspend fun execute(project: Project) {
        log.info("MAIP plugin initializing for project: ${project.name}")

        val projectService = project.getService(MAIPProjectService::class.java)
        projectService.initialize()

        val autoReceiptService = project.getService(AutoReceiptService::class.java)
        autoReceiptService.initialize()

        checkCredentialsAndNotify(project)

        log.info("MAIP plugin initialized for project: ${project.name}")
    }

    /**
     * Checks whether stored API credentials exist. If not, shows a balloon
     * notification with an action to open the MAIP settings panel where
     * the user can register.
     */
    private fun checkCredentialsAndNotify(project: Project) {
        val registrationService = RegistrationService.getInstance()
        if (registrationService.hasStoredCredentials()) {
            return
        }

        log.info("No MAIP credentials found, prompting user to register")

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("MAIP Notifications")
            .createNotification(
                "Get started with Truthlocks",
                "Register for a free API key to enable machine identity receipts and trust scoring.",
                NotificationType.INFORMATION
            )
            .addAction(NotificationAction.createSimple("Register Now") {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "MAIP")
            })

        notification.notify(project)
    }
}
