package com.deniz.eda.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionSafetyTest {
    @Test
    fun blocksConsequentialTargetsInMultipleLanguages() {
        assertTrue(PhoneActionSafety.isDangerousTarget("Send now"))
        assertTrue(PhoneActionSafety.isDangerousTarget("Satın al"))
        assertTrue(PhoneActionSafety.isDangerousTarget("Para gönder"))
        assertTrue(PhoneActionSafety.isDangerousTarget("پرداخت نهایی"))
        assertTrue(PhoneActionSafety.isDangerousTarget("Delete account"))
    }

    @Test
    fun allowsOrdinaryNavigationAndSearchLabels() {
        assertFalse(PhoneActionSafety.isDangerousTarget("Search"))
        assertFalse(PhoneActionSafety.isDangerousTarget("YouTube"))
        assertFalse(PhoneActionSafety.isDangerousTarget("Back"))
        assertFalse(PhoneActionSafety.isDangerousTarget("Open settings"))
    }

    @Test
    fun detectsFinancialAndCheckoutContexts() {
        assertTrue(PhoneActionSafety.isSensitiveScreen("Online banking - Transfer money"))
        assertTrue(PhoneActionSafety.isSensitiveScreen("Ödeme bilgileri"))
        assertTrue(PhoneActionSafety.isSensitiveScreen("انتقال وجه"))
        assertFalse(PhoneActionSafety.isSensitiveScreen("YouTube search results"))
    }

    @Test
    fun detectsPasswordAndOneTimeCodeFields() {
        assertTrue(PhoneActionSafety.isSensitiveField("Password"))
        assertTrue(PhoneActionSafety.isSensitiveField("SMS doğrulama kodu"))
        assertTrue(PhoneActionSafety.isSensitiveField("رمز پویا"))
        assertFalse(PhoneActionSafety.isSensitiveField("Search"))
        assertFalse(PhoneActionSafety.isSensitiveField("Write a comment"))
    }
}
