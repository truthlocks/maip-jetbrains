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

package com.truthlocks.maip.jetbrains.icons

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Centralized icon references for the MAIP plugin.
 *
 * Uses standard IntelliJ platform icons to maintain visual consistency
 * across all JetBrains IDEs without requiring custom icon assets.
 */
object MAIPIcons {

    /** Icon for the MAIP tool window. */
    val TOOL_WINDOW: Icon = AllIcons.Actions.Checked

    /** Icon representing a receipt artifact. */
    val RECEIPT: Icon = AllIcons.Actions.AddFile

    /** Icon representing a verified receipt. */
    val RECEIPT_VERIFIED: Icon = AllIcons.Actions.Checked

    /** Icon representing a failed receipt verification. */
    val RECEIPT_FAILED: Icon = AllIcons.General.Error

    /** Icon representing a machine agent. */
    val AGENT: Icon = AllIcons.General.User

    /** Icon representing an active/healthy agent. */
    val AGENT_ACTIVE: Icon = AllIcons.General.InspectionsOK

    /** Icon representing a suspended agent. */
    val AGENT_SUSPENDED: Icon = AllIcons.General.Warning

    /** Icon representing a revoked agent. */
    val AGENT_REVOKED: Icon = AllIcons.General.Error

    /** Icon for trust score display. */
    val TRUST: Icon = AllIcons.Actions.Checked

    /** Icon for high trust (>= 0.8). */
    val TRUST_HIGH: Icon = AllIcons.General.InspectionsOK

    /** Icon for medium trust (0.5 - 0.79). */
    val TRUST_MEDIUM: Icon = AllIcons.General.Warning

    /** Icon for low trust (< 0.5). */
    val TRUST_LOW: Icon = AllIcons.General.Error

    /** Icon for the audit/export action. */
    val AUDIT: Icon = AllIcons.ToolbarDecorator.Export

    /** Icon for delegation chains. */
    val DELEGATION: Icon = AllIcons.Hierarchy.Subtypes

    /** Icon for refresh actions. */
    val REFRESH: Icon = AllIcons.Actions.Refresh

    /** Icon for add/register actions. */
    val ADD: Icon = AllIcons.General.Add

    /**
     * Returns the appropriate trust icon based on a numeric trust score.
     *
     * @param score The trust score in the range 0.0 to 1.0.
     * @return The icon corresponding to the trust level.
     */
    fun trustIcon(score: Double): Icon = when {
        score >= 0.8 -> TRUST_HIGH
        score >= 0.5 -> TRUST_MEDIUM
        else -> TRUST_LOW
    }

    /**
     * Returns the appropriate agent status icon.
     *
     * @param status The agent status string (active, suspended, revoked).
     * @return The icon corresponding to the agent status.
     */
    fun agentStatusIcon(status: String): Icon = when (status.lowercase()) {
        "active" -> AGENT_ACTIVE
        "suspended" -> AGENT_SUSPENDED
        "revoked" -> AGENT_REVOKED
        else -> AGENT
    }
}
