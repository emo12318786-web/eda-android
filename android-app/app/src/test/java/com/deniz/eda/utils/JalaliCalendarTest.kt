package com.deniz.eda.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class JalaliCalendarTest {
    @Test
    fun convertsPersianNewYearBoundary() {
        val lastDay = JalaliCalendar.miladidenSemsiyeCevir(2025, 3, 20)
        assertEquals(1403, lastDay.yil)
        assertEquals(12, lastDay.ay)
        assertEquals(30, lastDay.gun)

        val newYear = JalaliCalendar.miladidenSemsiyeCevir(2025, 3, 21)
        assertEquals(1404, newYear.yil)
        assertEquals(1, newYear.ay)
        assertEquals(1, newYear.gun)
    }

    @Test
    fun monthNamesAreAvailableForValidMonths() {
        val date = JalaliCalendar.miladidenSemsiyeCevir(2025, 3, 21)
        assertEquals("Ferverdin", date.ayAdi())
    }
}
