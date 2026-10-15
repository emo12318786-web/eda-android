package com.deniz.eda.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    @Test
    fun matchesExactPhrases() {
        assertTrue(FuzzyMatcher.herhangiBiri("feneri aç", "feneri aç"))
    }

    @Test
    fun doesNotMatchBlankInput() {
        assertFalse(FuzzyMatcher.icindeVarMi("", "konum"))
        assertFalse(FuzzyMatcher.icindeVarMi("   ", "saat"))
    }

    @Test
    fun avoidsShortWordFalsePositive() {
        assertFalse(FuzzyMatcher.icindeVarMi("benimle konuş", "ai"))
    }
}
