package com.deniz.eda.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTaskRouterTest {
    @Test
    fun recognizesTurkishScreenCommands() {
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, ekranı oku"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, aşağı kaydır ve ekrandakini oku"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, bildirimleri aç"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, ekrandaki metne dokun: «Ayarlar»"))
    }

    @Test
    fun recognizesAzerbaijaniLatinScreenCommands() {
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, ekranı oxu"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, aşağı sürüşdür"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, yuxarı sürüşdür"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, bildirişləri aç"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, ana səhifəyə qayıt"))
        assertTrue(PhoneTaskRouter.isPhoneIntent("Eda, ekrandaki metne toxun: «Parametrlər»"))
    }

    @Test
    fun ordinaryConversationDoesNotBecomeScreenAutomation() {
        assertFalse(PhoneTaskRouter.isPhoneIntent("Hayatın anlamı nedir?"))
        assertFalse(PhoneTaskRouter.isPhoneIntent("Merhaba, nasılsın?"))
        assertFalse(PhoneTaskRouter.isPhoneIntent("Telefon ekranlarının tarihini anlat"))
    }
}
