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

package com.truthlocks.maip.jetbrains.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiRecursiveElementVisitor
import com.truthlocks.maip.jetbrains.services.MAIPProjectService
import com.truthlocks.maip.jetbrains.services.TrustService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Code inspection that detects MAIP agent IDs in source code and
 * annotates them with trust score information.
 *
 * Scans string literals and comments for patterns matching MAIP agent
 * ID format (UUID v4 prefixed with `maip-agent:` or `agent_id:`).
 * When found, displays an informational annotation with the agent's
 * current trust level from the cached data.
 */
class AgentIdInspection : LocalInspectionTool() {

    companion object {
        /**
         * Pattern matching MAIP agent ID references in code.
         * Matches formats like:
         * - `maip-agent:550e8400-e29b-41d4-a716-446655440000`
         * - `agent_id: "550e8400-e29b-41d4-a716-446655440000"`
         * - `agentId = "550e8400-e29b-41d4-a716-446655440000"`
         */
        private val AGENT_ID_PATTERN: Pattern = Pattern.compile(
            """(?:maip[-_]agent|agent[-_]id)\s*[:=]\s*"?([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"?""",
            Pattern.CASE_INSENSITIVE
        )

        /**
         * Standalone UUID pattern for broader detection in MAIP-related contexts.
         */
        private val UUID_PATTERN: Pattern = Pattern.compile(
            """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""",
            Pattern.CASE_INSENSITIVE
        )
    }

    override fun getShortName(): String = "MAIPAgentIdInspection"

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return object : PsiRecursiveElementVisitor() {

            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                if (element is PsiComment || isStringLiteral(element)) {
                    val text = element.text
                    checkForAgentIds(element, text, holder)
                }
            }
        }
    }

    /**
     * Checks a text for MAIP agent ID references and registers inspection results.
     */
    private fun checkForAgentIds(element: PsiElement, text: String, holder: ProblemsHolder) {
        val matcher = AGENT_ID_PATTERN.matcher(text)
        while (matcher.find()) {
            val agentId = matcher.group(1)
            val trustInfo = lookupTrustInfo(element.project, agentId)
            val message = "MAIP Agent: $agentId — $trustInfo"

            holder.registerProblem(
                element,
                message,
                ProblemHighlightType.INFORMATION,
                ShowTrustQuickFix(agentId)
            )
        }
    }

    /**
     * Looks up trust information for an agent from the cached data.
     *
     * @param project The current project.
     * @param agentId The agent ID to look up.
     * @return A formatted trust string.
     */
    private fun lookupTrustInfo(project: Project, agentId: String): String {
        val projectService = MAIPProjectService.getInstance(project)
        val trustScores = projectService.trustScores

        val trustObj = trustScores.find {
            it.get("agent_id")?.asString == agentId || it.get("id")?.asString == agentId
        }

        if (trustObj == null) return "Trust: unknown (not in cache)"

        val score = TrustService.extractScore(trustObj)
        val level = TrustService.trustLevel(score)
        return "Trust: ${TrustService.formatScore(score)} ($level)"
    }

    /**
     * Checks if a PSI element is a string literal expression.
     */
    private fun isStringLiteral(element: PsiElement): Boolean {
        return element.node?.elementType?.toString()?.contains("STRING") == true
    }

    /**
     * Quick fix that opens the trust score dialog for the detected agent ID.
     */
    private class ShowTrustQuickFix(private val agentId: String) : LocalQuickFix {

        override fun getName(): String = "Show MAIP trust score for $agentId"

        override fun getFamilyName(): String = "MAIP"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val trustService = TrustService.getInstance(project)
            CoroutineScope(Dispatchers.IO).launch {
                val result = trustService.getTrustScore(agentId)
                if (result != null) {
                    val score = TrustService.extractScore(result)
                    val level = TrustService.trustLevel(score)
                    val formatted = TrustService.formatScore(score)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "Agent: $agentId\nTrust Score: $formatted ($level)\n\nRaw: $result",
                            "MAIP Trust Score"
                        )
                    }
                }
            }
        }
    }
}
