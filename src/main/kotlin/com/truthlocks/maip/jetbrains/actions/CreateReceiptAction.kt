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

package com.truthlocks.maip.jetbrains.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.truthlocks.maip.jetbrains.services.ReceiptService
import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Action that creates a MAIP integrity receipt for the currently
 * selected file in the editor or project view.
 *
 * Available from the Tools > MAIP menu and the editor context menu.
 */
class CreateReceiptAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (!MAIPSettingsState.getInstance().isConfigured()) {
            Messages.showErrorDialog(
                project,
                "MAIP is not configured. Please set your API URL, key, and tenant ID in Settings > Tools > MAIP.",
                "MAIP Not Configured"
            )
            return
        }

        if (file == null || file.isDirectory) {
            Messages.showErrorDialog(
                project,
                "Please select a file to create a receipt for.",
                "No File Selected"
            )
            return
        }

        val description = Messages.showInputDialog(
            project,
            "Description for the receipt (optional):",
            "Create MAIP Receipt",
            null,
            "Receipt for ${file.name}",
            null
        ) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val receiptService = ReceiptService.getInstance(project)
            receiptService.createReceiptForFile(file, description)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && (file == null || !file.isDirectory)
    }
}
