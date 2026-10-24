package com.deniz.eda.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenReadPolicyTest {
    @Test
    fun acceptsClearScreenReadingRequests() {
        assertTrue(ScreenReadPolicy.isExplicitReadRequest("Eda, ekranda ne yazıyor?"))
        assertTrue(ScreenReadPolicy.isExplicitReadRequest("Ekranı oku"))
        assertTrue(ScreenReadPolicy.isExplicitReadRequest("Read the screen"))
        assertTrue(ScreenReadPolicy.isExplicitReadRequest("What is on the screen?"))
        assertTrue(ScreenReadPolicy.isExplicitReadRequest("روی صفحه چی نوشته"))
    }

    @Test
    fun rejectsUnrelatedCommandsAndConversation() {
        assertFalse(ScreenReadPolicy.isExplicitReadRequest("Ekran ayarlarını aç"))
        assertFalse(ScreenReadPolicy.isExplicitReadRequest("Parlaklığı azalt"))
        assertFalse(ScreenReadPolicy.isExplicitReadRequest("Merhaba, nasılsın?"))
        assertFalse(ScreenReadPolicy.isExplicitReadRequest(""))
    }
}