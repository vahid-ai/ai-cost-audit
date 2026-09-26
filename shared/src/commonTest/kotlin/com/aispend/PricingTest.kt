package com.aispend

import com.aispend.model.ProviderId
import com.aispend.model.UsageRecord
import com.aispend.pricing.CostEstimator
import com.aispend.pricing.PricingTable
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PricingTest {
    private fun record(model: String, input: Long = 1_000_000, output: Long = 0, cached: Long = 0, cost: Double? = null) =
        UsageRecord(ProviderId.OPENAI, model, LocalDate(2026, 9, 1), input, output, cached, cost)

    @Test
    fun knownModelExact() {
        val price = PricingTable.priceFor("gpt-5")
        assertNotNull(price)
        assertEquals(1.25, price.inputPer1M)
        assertEquals(10.0, price.outputPer1M)
    }

    @Test
    fun prefixMatch() {
        val price = PricingTable.priceFor("gpt-4.1-2025-04-14")
        assertNotNull(price)
        assertEquals(2.0, price.inputPer1M)
    }

    @Test
    fun unknownModel() {
        assertNull(PricingTable.priceFor("totally-made-up-model-9000"))
    }

    @Test
    fun estimatorUsesTableWhenCostNull() {
        // 1M input + 1M output on gpt-5 => 1.25 + 10.0
        assertEquals(11.25, CostEstimator.estimate(record("gpt-5", output = 1_000_000)))
    }

    @Test
    fun estimatorPrefersRecordCost() {
        assertEquals(3.14, CostEstimator.estimate(record("gpt-5", cost = 3.14)))
    }

    @Test
    fun estimatorNullWhenUnpriced() {
        assertNull(CostEstimator.estimate(record("made-up-9000")))
    }

    @Test
    fun longestPrefixWins() {
        // "gpt-5-mini-..." should match gpt-5-mini not gpt-5
        val price = PricingTable.priceFor("gpt-5-mini-2025-08-07")
        assertNotNull(price)
        assertEquals(0.25, price.inputPer1M)
    }
}
