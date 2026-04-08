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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the MAIP REST API.
 *
 * Provides typed methods for agent management, receipt CRUD, trust scoring,
 * and audit export. All network calls run on [Dispatchers.IO] via coroutines
 * and include automatic retry with exponential backoff for transient errors
 * (HTTP 429 and 5xx).
 *
 * @param config The configuration snapshot defining endpoint, credentials, and timeouts.
 */
class MAIPApiClient(private val config: MAIPConfig) {

    private val log = Logger.getInstance(MAIPApiClient::class.java)
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val augmented = original.newBuilder()
                .header("X-API-Key", config.apiKey)
                .header("X-Tenant-ID", config.tenantId)
                .header("Accept", "application/json")
                .header("User-Agent", "MAIP-JetBrains/1.0.0")
                .build()
            chain.proceed(augmented)
        }
        .build()

    // -------------------------------------------------------------------------
    // Agent endpoints
    // -------------------------------------------------------------------------

    /**
     * Registers a new machine agent.
     *
     * @param name         The human-readable agent name.
     * @param capabilities A list of capability strings (e.g. `["code-review", "testing"]`).
     * @param parentId     Optional parent agent ID for delegation hierarchy.
     * @return A [JsonObject] representing the newly registered agent, or `null` on failure.
     */
    suspend fun registerAgent(
        name: String,
        capabilities: List<String>,
        parentId: String? = null
    ): JsonObject? {
        val body = JsonObject().apply {
            addProperty("name", name)
            add("capabilities", gson.toJsonTree(capabilities))
            if (parentId != null) addProperty("parent_id", parentId)
        }
        return post("/agents", body)
    }

    /**
     * Lists all agents visible to the current tenant.
     *
     * @param limit  Maximum number of agents to return.
     * @param offset Pagination offset.
     * @return A list of agent JSON objects, or an empty list on failure.
     */
    suspend fun listAgents(limit: Int = 50, offset: Int = 0): List<JsonObject> {
        val result = get("/agents?limit=$limit&offset=$offset")
        return parseList(result)
    }

    /**
     * Retrieves a single agent by ID.
     *
     * @param agentId The agent identifier.
     * @return The agent JSON object, or `null` if not found.
     */
    suspend fun getAgent(agentId: String): JsonObject? {
        return get("/agents/$agentId")
    }

    /**
     * Suspends an active agent.
     *
     * @param agentId The agent identifier.
     * @param reason  The reason for suspension.
     * @return The updated agent JSON object, or `null` on failure.
     */
    suspend fun suspendAgent(agentId: String, reason: String): JsonObject? {
        val body = JsonObject().apply {
            addProperty("status", "suspended")
            addProperty("reason", reason)
        }
        return patch("/agents/$agentId", body)
    }

    /**
     * Revokes an agent permanently.
     *
     * @param agentId The agent identifier.
     * @param reason  The reason for revocation.
     * @return The updated agent JSON object, or `null` on failure.
     */
    suspend fun revokeAgent(agentId: String, reason: String): JsonObject? {
        val body = JsonObject().apply {
            addProperty("status", "revoked")
            addProperty("reason", reason)
        }
        return patch("/agents/$agentId", body)
    }

    // -------------------------------------------------------------------------
    // Receipt endpoints
    // -------------------------------------------------------------------------

    /**
     * Creates a new integrity receipt.
     *
     * @param receiptType   The receipt type (e.g. `"code"`, `"build"`, `"commit"`).
     * @param description   A human-readable description.
     * @param artifactHash  The SHA-256 hash of the artifact.
     * @param agentId       The creating agent's ID.
     * @param metadata      Optional metadata key-value pairs.
     * @return The created receipt JSON object, or `null` on failure.
     */
    suspend fun createReceipt(
        receiptType: String,
        description: String,
        artifactHash: String,
        agentId: String,
        metadata: Map<String, String> = emptyMap()
    ): JsonObject? {
        val body = JsonObject().apply {
            addProperty("type", receiptType)
            addProperty("description", description)
            addProperty("artifact_hash", artifactHash)
            addProperty("agent_id", agentId)
            if (metadata.isNotEmpty()) {
                add("metadata", gson.toJsonTree(metadata))
            }
        }
        return post("/receipts", body)
    }

    /**
     * Lists receipts with optional filtering.
     *
     * @param type    Optional receipt type filter.
     * @param agentId Optional agent ID filter.
     * @param limit   Maximum results.
     * @param offset  Pagination offset.
     * @return A list of receipt JSON objects.
     */
    suspend fun listReceipts(
        type: String? = null,
        agentId: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<JsonObject> {
        val params = mutableListOf("limit=$limit", "offset=$offset")
        if (type != null) params.add("type=$type")
        if (agentId != null) params.add("agent_id=$agentId")
        val result = get("/receipts?${params.joinToString("&")}")
        return parseList(result)
    }

    /**
     * Retrieves a single receipt by ID.
     *
     * @param receiptId The receipt identifier.
     * @return The receipt JSON object, or `null` if not found.
     */
    suspend fun getReceipt(receiptId: String): JsonObject? {
        return get("/receipts/$receiptId")
    }

    /**
     * Verifies a receipt's integrity.
     *
     * @param receiptId The receipt identifier.
     * @return The verification result JSON object, or `null` on failure.
     */
    suspend fun verifyReceipt(receiptId: String): JsonObject? {
        return post("/receipts/$receiptId/verify", JsonObject())
    }

    // -------------------------------------------------------------------------
    // Trust endpoints
    // -------------------------------------------------------------------------

    /**
     * Retrieves the trust score for an agent.
     *
     * @param agentId The agent identifier.
     * @return The trust score JSON object, or `null` on failure.
     */
    suspend fun getTrustScore(agentId: String): JsonObject? {
        return get("/trust/$agentId")
    }

    /**
     * Retrieves trust scores for all agents.
     *
     * @return A list of trust score JSON objects.
     */
    suspend fun listTrustScores(): List<JsonObject> {
        val result = get("/trust")
        return parseList(result)
    }

    // -------------------------------------------------------------------------
    // Delegation endpoints
    // -------------------------------------------------------------------------

    /**
     * Retrieves the delegation chain for a receipt.
     *
     * @param receiptId The receipt identifier.
     * @return The delegation chain JSON object, or `null` on failure.
     */
    suspend fun getDelegationChain(receiptId: String): JsonObject? {
        return get("/receipts/$receiptId/delegation-chain")
    }

    // -------------------------------------------------------------------------
    // Audit endpoints
    // -------------------------------------------------------------------------

    /**
     * Exports an audit report for the tenant.
     *
     * @param startDate  ISO-8601 start date.
     * @param endDate    ISO-8601 end date.
     * @param format     Export format (`json` or `csv`).
     * @return The raw response body as a string, or `null` on failure.
     */
    suspend fun exportAudit(
        startDate: String,
        endDate: String,
        format: String = "json"
    ): String? = withContext(Dispatchers.IO) {
        val url = "${config.apiUrl}/audit/export?start=$startDate&end=$endDate&format=$format"
        val request = Request.Builder().url(url).get().build()
        executeWithRetry(request)?.use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }

    // -------------------------------------------------------------------------
    // HTTP primitives with retry
    // -------------------------------------------------------------------------

    /**
     * Sends an HTTP GET request and parses the response as a [JsonObject].
     */
    private suspend fun get(path: String): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.apiUrl}$path")
            .get()
            .build()
        executeAndParse(request)
    }

    /**
     * Sends an HTTP POST request with a JSON body and parses the response.
     */
    private suspend fun post(path: String, body: JsonObject): JsonObject? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${config.apiUrl}$path")
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()
            executeAndParse(request)
        }

    /**
     * Sends an HTTP PATCH request with a JSON body and parses the response.
     */
    private suspend fun patch(path: String, body: JsonObject): JsonObject? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${config.apiUrl}$path")
                .patch(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()
            executeAndParse(request)
        }

    /**
     * Executes a request with retry and parses the response body as a [JsonObject].
     */
    private fun executeAndParse(request: Request): JsonObject? {
        return executeWithRetry(request)?.use { response ->
            if (!response.isSuccessful) {
                log.warn("MAIP API returned ${response.code}: ${response.message}")
                return@use null
            }
            val bodyStr = response.body?.string() ?: return@use null
            try {
                gson.fromJson(bodyStr, JsonObject::class.java)
            } catch (e: Exception) {
                log.warn("Failed to parse MAIP API response", e)
                null
            }
        }
    }

    /**
     * Executes an HTTP request with exponential backoff retry for transient errors.
     *
     * Retries on HTTP 429 (rate limited) and 5xx (server error), up to
     * [MAIPConfig.maxRetries] attempts. The initial backoff is 1 second,
     * doubling on each retry.
     *
     * @param request The OkHttp request to execute.
     * @return The [Response] if successful or non-retryable, `null` if all retries exhausted.
     */
    private fun executeWithRetry(request: Request): Response? {
        var lastException: IOException? = null
        var backoffMs = 1_000L

        for (attempt in 0..config.maxRetries) {
            try {
                val response = httpClient.newCall(request).execute()
                val code = response.code

                if (code != 429 && code < 500) {
                    return response
                }

                response.close()
                log.info(
                    "MAIP API returned $code, retrying (attempt ${attempt + 1}/${config.maxRetries})"
                )
            } catch (e: IOException) {
                lastException = e
                log.info(
                    "MAIP API request failed: ${e.message}, retrying (attempt ${attempt + 1}/${config.maxRetries})"
                )
            }

            if (attempt < config.maxRetries) {
                Thread.sleep(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(16_000L)
            }
        }

        log.warn("MAIP API request failed after ${config.maxRetries} retries", lastException)
        return null
    }

    /**
     * Parses a JSON response that may contain a list under a `data` key,
     * or may itself be a JSON array.
     */
    private fun parseList(result: JsonObject?): List<JsonObject> {
        if (result == null) return emptyList()
        val dataElement = result.get("data") ?: result.get("items") ?: return listOf(result)
        if (!dataElement.isJsonArray) return emptyList()
        val type = object : TypeToken<List<JsonObject>>() {}.type
        return gson.fromJson(dataElement, type) ?: emptyList()
    }

    /**
     * Closes the underlying HTTP client and releases connections.
     */
    fun shutdown() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
