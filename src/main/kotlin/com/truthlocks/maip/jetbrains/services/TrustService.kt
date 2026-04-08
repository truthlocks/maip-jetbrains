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

import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color

/**
 * Service for querying and interpreting trust scores.
 *
 * Provides typed accessors for trust data and color-coding helpers
 * used by the UI layer.
 */
class TrustService(private val project: Project) {

    private val log = Logger.getInstance(TrustService::class.java)

    /**
     * Retrieves the trust score for a specific agent.
     *
     * @param agentId The agent identifier.
     * @return The trust score JSON object containing `score`, `level`, and `factors`,
     *         or `null` if the lookup fails.
     */
    suspend fun getTrustScore(agentId: String): JsonObject? = withContext(Dispatchers.IO) {
        val client = MAIPProjectService.getInstance(project).getClient() ?: return@withContext null
        try {
            client.getTrustScore(agentId)
        } catch (e: Exception) {
            log.warn("Failed to get trust score for $agentId", e)
            null
        }
    }

    /**
     * Retrieves trust scores for all agents.
     *
     * @return A list of trust score JSON objects.
     */
    suspend fun listTrustScores(): List<JsonObject> = withContext(Dispatchers.IO) {
        val client = MAIPProjectService.getInstance(project).getClient() ?: return@withContext emptyList()
        try {
            client.listTrustScores()
        } catch (e: Exception) {
            log.warn("Failed to list trust scores", e)
            emptyList()
        }
    }

    companion object {

        /** Color for high trust scores (>= 0.8). */
        val HIGH_TRUST_COLOR: Color = Color(0, 153, 51)

        /** Color for medium trust scores (0.5 - 0.79). */
        val MEDIUM_TRUST_COLOR: Color = Color(204, 153, 0)

        /** Color for low trust scores (< 0.5). */
        val LOW_TRUST_COLOR: Color = Color(204, 0, 0)

        /**
         * Returns the project-level [TrustService] instance.
         */
        fun getInstance(project: Project): TrustService {
            return project.getService(TrustService::class.java)
        }

        /**
         * Returns the display color for a given trust score.
         *
         * @param score Trust score in the range 0.0 to 1.0.
         * @return The appropriate color.
         */
        fun trustColor(score: Double): Color = when {
            score >= 0.8 -> HIGH_TRUST_COLOR
            score >= 0.5 -> MEDIUM_TRUST_COLOR
            else -> LOW_TRUST_COLOR
        }

        /**
         * Returns a human-readable trust level label.
         *
         * @param score Trust score in the range 0.0 to 1.0.
         * @return One of "High", "Medium", or "Low".
         */
        fun trustLevel(score: Double): String = when {
            score >= 0.8 -> "High"
            score >= 0.5 -> "Medium"
            else -> "Low"
        }

        /**
         * Formats a trust score as a percentage string.
         *
         * @param score Trust score in the range 0.0 to 1.0.
         * @return Formatted string like "85.0%".
         */
        fun formatScore(score: Double): String {
            return "%.1f%%".format(score * 100)
        }

        /**
         * Extracts the numeric score from a trust JSON object.
         *
         * @param trustObj The trust JSON object.
         * @return The score value, or 0.0 if not present.
         */
        fun extractScore(trustObj: JsonObject?): Double {
            return trustObj?.get("score")?.asDouble ?: 0.0
        }
    }
}
