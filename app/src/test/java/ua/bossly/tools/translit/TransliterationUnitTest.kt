package ua.bossly.tools.translit

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class TransliterationUnitTest {

    val origin = listOf(
        "Андрій", "Богдан", "Жанна", "Наталія", "Петро", "Соломія", "Шевченко")
    val passport_2010 = listOf(
        "Andrii", "Bohdan", "Zhanna", "Nataliia", "Petro", "Solomiia", "Shevchenko")
    val geographic_1996 = listOf(
        "Andrii", "Bohdan", "Zhanna", "Nataliia", "Petro", "Solomiia", "Shevchenko")

    @Test
    fun testPassport_2010() {
        for (index in origin.indices) {
            assertEquals(
                passport_2010[index].toLowerCase(),
                TransliterationUtils.convert(origin[index].toLowerCase()))
        }
    }

    @Test
    fun testGeographic_1996() {
        for (index in origin.indices) {
            assertEquals(
                geographic_1996[index].toLowerCase(),
                TransliterationUtils.convert(origin[index].toLowerCase()))
        }
    }
}