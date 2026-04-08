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

package com.truthlocks.maip.jetbrains.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Centralized notification helper for the MAIP plugin.
 *
 * All user-facing balloon notifications are dispatched through this object
 * to ensure consistent grouping and presentation.
 */
object MAIPNotifier {

    private const val GROUP_ID = "MAIP Notifications"

    /**
     * Shows an informational balloon notification.
     *
     * @param project The current project context.
     * @param title   The notification title.
     * @param content The notification body text.
     */
    fun info(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    /**
     * Shows a warning balloon notification.
     *
     * @param project The current project context.
     * @param title   The notification title.
     * @param content The notification body text.
     */
    fun warn(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.WARNING)
    }

    /**
     * Shows an error balloon notification.
     *
     * @param project The current project context.
     * @param title   The notification title.
     * @param content The notification body text.
     */
    fun error(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.ERROR)
    }

    /**
     * Dispatches a notification of the given type through the MAIP notification group.
     *
     * @param project The current project context.
     * @param title   The notification title.
     * @param content The notification body text.
     * @param type    The severity level.
     */
    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}
