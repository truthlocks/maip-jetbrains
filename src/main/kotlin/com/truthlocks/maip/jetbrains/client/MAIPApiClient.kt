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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
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
            val builder = original.newBuilder()
                .header("X-API-Key", config.apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "MAIP-JetBrains/1.0.0")
            if (config.tenantId.isNotBlank() && !config.apiKey.startsWith("tl_live_")) {
                builder.header("X-Tenant-ID", config.tenantId)
            }
            chain.proceed(builder.build())
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
        agentType: String = "tool",
        scopes: List<String> = emptyList(),
        parentId: String? = null
    ): JsonObject? {
        val body = JsonObject().apply {
            addProperty("display_name", name)
            addProperty("agent_type", agentType)
            add("scopes", gson.toJsonTree(scopes))
            if (parentId != null) addProperty("parent_agent_id", parentId)
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
        return get("/agents/${URLEncoder.encode(agentId, "UTF-8")}")
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
            addProperty("reason", reason)
        }
        return post("/agents/${URLEncoder.encode(agentId, "UTF-8")}/suspend", body)
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
            addProperty("reason", reason)
        }
        return post("/agents/${URLEncoder.encode(agentId, "UTF-8")}/revoke", body)
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
        action: String,
        agentId: String,
        payload: Map<String, Any> = emptyMap()
    ): JsonObject? {
        val body = JsonObject().apply {
            addProperty("receipt_type", receiptType)
            addProperty("action", action)
            addProperty("agent_id", agentId)
            add("payload", gson.toJsonTree(payload))
        }
        return post("/agent-receipts", body)
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
        receiptType: String? = null,
        agentId: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<JsonObject> {
        val params = mutableListOf("limit=$limit", "offset=$offset")
        if (receiptType != null) params.add("receipt_type=$receiptType")
        if (agentId != null) params.add("agent_id=${URLEncoder.encode(agentId, "UTF-8")}")
        val result = get("/agent-receipts/filter?${params.joinToString("&")}")
        return parseList(result)
    }

    /**
     * Retrieves a single receipt by ID.
     *
     * @param receiptId The receipt identifier.
     * @return The receipt JSON object, or `null` if not found.
     */
    suspend fun getReceipt(receiptId: String): JsonObject? {
        return get("/agent-receipts/${URLEncoder.encode(receiptId, "UTF-8")}")
    }

    /**
     * Verifies a receipt's integrity.
     *
     * @param receiptId The receipt identifier.
     * @return The verification result JSON object, or `null` on failure.
     */
    suspend fun verifyReceipt(receiptId: String): JsonObject? {
        val receipt = getReceipt(receiptId) ?: return null
        val status = receipt.get("status")?.asString ?: "unknown"
        val valid = status == "valid"
        return JsonObject().apply {
            addProperty("valid", valid)
            addProperty("verdict", if (valid) "PASS" else "FAIL")
            addProperty("status", status)
            addProperty("details", if (valid) "Receipt signature and chain verified successfully" else "Receipt status is $status")
            if (status == "expired") addProperty("warning", "Receipt has expired")
            if (status == "superseded") addProperty("warning", "Receipt has been superseded by a newer receipt")
        }
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
        return get("/agents/${URLEncoder.encode(agentId, "UTF-8")}/trust-score")
    }

    /**
     * Retrieves trust history for an agent.
     *
     * @param agentId The agent identifier.
     * @param limit   Maximum number of entries.
     * @return A list of trust history JSON objects.
     */
    suspend fun getTrustHistory(agentId: String, limit: Int = 50): List<JsonObject> {
        val result = get("/agents/${URLEncoder.encode(agentId, "UTF-8")}/trust-history?limit=$limit")
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
    suspend fun getDelegationTree(agentId: String): JsonObject? {
        return get("/agents/${URLEncoder.encode(agentId, "UTF-8")}/delegation-tree")
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
        val url = "${config.apiUrl}/compliance/reports?start=$startDate&end=$endDate&format=$format"
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
        val dataElement = result.get("agents")
            ?: result.get("receipts")
            ?: result.get("delegations")
            ?: result.get("data")
            ?: result.get("items")
            ?: return listOf(result)
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
