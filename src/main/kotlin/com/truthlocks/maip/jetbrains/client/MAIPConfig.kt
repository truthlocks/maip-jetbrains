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

package com.truthlocks.maip.jetbrains.client

import com.truthlocks.maip.jetbrains.settings.MAIPSettingsState

/**
 * Immutable configuration snapshot consumed by [MAIPApiClient].
 *
 * Constructed from the current [MAIPSettingsState] so that configuration
 * changes are picked up on the next API call without requiring a restart.
 *
 * @property apiUrl    The base URL for the MAIP API (e.g. `https://api.truthlocks.com/maip/v1`).
 * @property apiKey    The API key used for authentication.
 * @property tenantId  The tenant identifier for multi-tenant isolation.
 * @property agentId   The default agent ID to use when creating receipts.
 * @property timeoutMs The HTTP request timeout in milliseconds.
 * @property maxRetries The maximum number of retries for transient failures.
 */
data class MAIPConfig(
    val apiUrl: String,
    val apiKey: String,
    val tenantId: String,
    val agentId: String,
    val timeoutMs: Long = 30_000L,
    val maxRetries: Int = 3
) {

    /**
     * Returns `true` if the minimum required configuration fields are present.
     */
    val isValid: Boolean
        get() = apiUrl.isNotBlank() && apiKey.isNotBlank() && tenantId.isNotBlank()

    companion object {

        /**
         * Builds a [MAIPConfig] from the current persistent settings.
         *
         * @return A config snapshot reflecting the current IDE settings.
         */
        fun fromSettings(): MAIPConfig {
            val state = MAIPSettingsState.getInstance()
            return MAIPConfig(
                apiUrl = state.apiUrl.trimEnd('/'),
                apiKey = state.getApiKey(),
                tenantId = state.tenantId,
                agentId = state.agentId,
                timeoutMs = state.timeoutMs,
                maxRetries = state.maxRetries
            )
        }
    }
}
