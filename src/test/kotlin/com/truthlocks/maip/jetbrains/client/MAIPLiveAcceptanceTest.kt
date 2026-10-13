package com.truthlocks.maip.jetbrains.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** Explicit opt-in; reuses a synthetic QA receipt and does not create users, keys or charges. */
@EnabledIfEnvironmentVariable(named = "TRUTHLOCKS_QA_TOKEN", matches = ".+")
class MAIPLiveAcceptanceTest {
    @Test
    fun `live QA receipt and agent workflows`() = runBlocking {
        val receiptId = System.getenv("TRUTHLOCKS_QA_RECEIPT") ?: error("QA receipt is required")
        val client = MAIPApiClient(MAIPConfig(
            apiUrl = "https://api.truthlocks.com/v1", apiKey = "", tenantId = "", agentId = "",
            bearerToken = System.getenv("TRUTHLOCKS_QA_TOKEN"), maxRetries = 0
        ))
        try {
            val receipt = client.getReceipt(receiptId) ?: error("QA receipt unavailable")
            val agentId = receipt.get("agent_id").asString
            val agent = client.getAgent(agentId) ?: error("QA agent unavailable")
            assertNotNull(agent.get("agent_id"))
            assertTrue(client.listAgents(100).any { it.get("agent_id")?.asString == agentId })
            assertNotNull(client.getTrustScore(agentId)?.get("score"))
            val verified = client.verifyReceipt(receiptId) ?: error("Verification unavailable")
            assertTrue(verified.get("valid").asBoolean)
            assertTrue(verified.get("signature_verified").asBoolean)
            assertEquals("VALID", verified.get("verdict").asString)
            assertTrue(client.listReceipts(agentId = agentId, limit = 100).isNotEmpty())
            assertNotNull(client.getDelegationChain(receiptId))
        } finally { client.shutdown() }
    }
}
