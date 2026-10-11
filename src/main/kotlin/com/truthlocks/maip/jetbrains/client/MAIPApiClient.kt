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
import com.google.gson.JsonParser
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset
import java.security.MessageDigest
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
    private val baseUrl = config.apiUrl.trimEnd('/').replace("/maip/v1", "/v1")
    private fun encoded(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    private val jsonMediaType = "application/json".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val augmented = original.newBuilder()
                .header("X-API-Key", config.apiKey)

                .header("Accept", "application/json")
                .header("User-Agent", "MAIP-JetBrains/1.1.0")
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
            addProperty("display_name", name)
            addProperty("agent_type", "service")
            add("scopes", gson.toJsonTree(capabilities))
            if (parentId != null) addProperty("parent_agent_id", parentId)
        }
        return post("/agents", body)?.let { agentView(it) }
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
        return parseList(result).map { agentView(it) }
    }

    /**
     * Retrieves a single agent by ID.
     *
     * @param agentId The agent identifier.
     * @return The agent JSON object, or `null` if not found.
     */
    suspend fun getAgent(agentId: String): JsonObject? {
        return get("/agents/${encoded(agentId)}")?.let { agentView(it) }
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
        return post("/agents/${encoded(agentId)}/suspend", body)
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
        return post("/agents/${encoded(agentId)}/revoke", body)
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
            addProperty("receipt_type", "action")
            addProperty("action", description)
            addProperty("agent_id", agentId)
            val receiptMetadata = metadata + mapOf("kind" to receiptType)
            add("metadata", gson.toJsonTree(if (artifactHash.isBlank()) receiptMetadata else receiptMetadata + mapOf("artifact_hash" to artifactHash)))
        }
        return post("/agent-receipts", body)?.let { receiptView(it) }
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
        if (type != null) params.add("q=${encoded(type)}")
        if (agentId != null) params.add("agent_id=${encoded(agentId)}")
        val result = get("/agent-receipts/filter?${params.joinToString("&")}")
        return parseList(result).map { receiptView(it) }.filter {
            type == null || it.get("type")?.takeUnless { value -> value.isJsonNull }?.asString == type
        }
    }

    /**
     * Retrieves a single receipt by ID.
     *
     * @param receiptId The receipt identifier.
     * @return The receipt JSON object, or `null` if not found.
     */
    suspend fun getReceipt(receiptId: String): JsonObject? {
        return get("/agent-receipts/${encoded(receiptId)}")?.let { receiptView(it) }
    }

    /**
     * Verifies a receipt's integrity.
     *
     * @param receiptId The receipt identifier.
     * @return The verification result JSON object, or `null` on failure.
     */
    suspend fun verifyReceipt(receiptId: String): JsonObject? {
        val result = get("/agent-receipts/verify/${encoded(receiptId)}") ?: return null
        val valid = result.get("valid")?.asBoolean == true && result.get("signature_verified")?.asBoolean == true && result.get("verdict")?.asString == "VALID"
        result.addProperty("valid", valid)
        return result
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
        return get("/agents/${encoded(agentId)}/trust-score")?.apply {
            get("trust_score")?.let { add("score", it) }
            get("trust_level")?.let { add("level", it) }
        }
    }

    /**
     * Retrieves trust scores for all agents.
     *
     * @return A list of trust score JSON objects.
     */
    suspend fun listTrustScores(): List<JsonObject> {
        return listAgents(limit = 100).mapNotNull { agent ->
            agent.get("agent_id")?.asString?.let { getTrustScore(it) }
        }
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
        val receipt = getReceipt(receiptId) ?: return null
        val agent = receipt.get("agent_id")?.asString ?: return null
        return get("/agents/${encoded(agent)}/delegation-tree")?.apply {
            addProperty("signature_verified", false)
            addProperty("note", "Delegation relationships are informational; this response does not verify delegation signatures.")
        }
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
        require(format in listOf("json", "csv")) { "Choose json or csv" }
        val from = LocalDate.parse(startDate)
        val through = LocalDate.parse(endDate)
        require(!through.isBefore(from) && through.toEpochDay() - from.toEpochDay() < 366) {
            "Choose an ordered date range of up to 366 days"
        }
        var job = post("/audit/exports", JsonObject().apply {
            addProperty("format", format.uppercase())
            addProperty("from_ts", from.atStartOfDay().toInstant(ZoneOffset.UTC).toString())
            addProperty("to_ts", through.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString())
        }) ?: return@withContext null
        val id = job.get("id")?.asString ?: return@withContext null
        repeat(60) {
            when (job.get("status")?.asString) {
                "COMPLETED" -> {
                    val request = Request.Builder().url("$baseUrl/audit/exports/${encoded(id)}/download").get().build()
                    return@withContext executeWithRetry(request)?.use { response ->
                        if (!response.isSuccessful) return@use null
                        val bytes = response.body?.bytes() ?: return@use null
                        val expected = job.get("sha256")?.takeUnless { it.isJsonNull }?.asString
                        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                        if (expected != null && !digest.equals(expected, ignoreCase = true)) {
                            throw IOException("Audit export checksum mismatch")
                        }
                        bytes.toString(Charsets.UTF_8)
                    }
                }
                "FAILED", "EXPIRED" -> throw IOException("Audit export $id did not complete successfully")
            }
            delay(1000)
            job = get("/audit/exports/${encoded(id)}") ?: return@withContext null
        }
        throw IOException("Export $id is still processing. Download it from Console > Audit exports when ready.")
    }

    // -------------------------------------------------------------------------
    // HTTP primitives with retry
    // -------------------------------------------------------------------------

    /**
     * Sends an HTTP GET request and parses the response as a [JsonObject].
     */
    private suspend fun get(path: String): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}$path")
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
                .url("${baseUrl}$path")
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
                .url("${baseUrl}$path")
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
                val parsed = JsonParser.parseString(bodyStr)
                when {
                    parsed.isJsonObject -> parsed.asJsonObject
                    parsed.isJsonArray -> JsonObject().apply { add("data", parsed) }
                    else -> null
                }
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

        val retries = if (request.method == "GET") config.maxRetries.coerceIn(0, 3) else 0
        for (attempt in 0..retries) {
            try {
                val response = httpClient.newCall(request).execute()
                val code = response.code

                if ((code != 429 && code < 500) || attempt == retries) {
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

            if (attempt < retries) {
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
    private fun agentView(agent: JsonObject): JsonObject {
        agent.get("agent_id")?.let { agent.add("internal_id", agent.get("id")); agent.add("id", it) }
        agent.get("display_name")?.let { agent.add("name", it) }
        return agent
    }

    private fun receiptView(receipt: JsonObject): JsonObject {
        receipt.get("receipt_id")?.let { receipt.add("internal_id", receipt.get("id")); receipt.add("id", it) }
        val metadata = receipt.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
        receipt.add("type", metadata?.get("kind") ?: receipt.get("receipt_type"))
        receipt.add("description", receipt.get("action"))
        metadata?.get("artifact_hash")?.let { receipt.add("artifact_hash", it) }
        return receipt
    }

    private fun parseList(result: JsonObject?): List<JsonObject> {
        if (result == null) return emptyList()
        val dataElement = result.get("data") ?: result.get("agents") ?: result.get("receipts") ?: result.get("items") ?: return listOf(result)
        if (!dataElement.isJsonArray) return emptyList()
        val type = object : TypeToken<List<JsonObject>>() {}.type
        return gson.fromJson(dataElement, type) ?: emptyList()
    }

    /**
     * Closes the underlying HTTP client and releases connections.
     */
    fun shutdown() {
        httpClient.dispatcher.cancelAll()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
