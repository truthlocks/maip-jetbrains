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

package com.truthlocks.maip.jetbrains.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory that creates the MAIP tool window with three tabs:
 * Receipts, Agents, and Trust Dashboard.
 *
 * Implements [DumbAware] so the tool window is available even
 * during indexing.
 */
class MAIPToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val receiptPanel = ReceiptPanel(project)
        val receiptContent = contentFactory.createContent(
            receiptPanel.component,
            "Receipts",
            false
        )
        toolWindow.contentManager.addContent(receiptContent)

        val agentPanel = AgentPanel(project)
        val agentContent = contentFactory.createContent(
            agentPanel.component,
            "Agents",
            false
        )
        toolWindow.contentManager.addContent(agentContent)

        val trustPanel = TrustDashboardPanel(project)
        val trustContent = contentFactory.createContent(
            trustPanel.component,
            "Trust Dashboard",
            false
        )
        toolWindow.contentManager.addContent(trustContent)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
