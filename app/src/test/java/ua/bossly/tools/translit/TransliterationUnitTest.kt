package ua.bossly.tools.translit

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Local unit tests for forward (Cyrillic -> Latin/Morse) and reverse (Latin/Morse -> Cyrillic)
 * transliteration with offline on-device Ukrainian word autocorrect.
 */
class TransliterationUnitTest {

    private fun loadTable(path: String): List<List<String>> {
        javaClass.getResourceAsStream(path)?.run {
            return csvReader().readAll(this)
        }

        return listOf()
    }

    private fun assertTransformation(map: String, tests: String) {
        val stream = javaClass.getResourceAsStream(map)!!
        val transform = FileTransliteration(stream)
        val table = loadTable(tests)

        for (row in table) {
            val origin = row[0]
            val expected = row[1]
            assertEquals(expected, WordTransformation.transform(origin, transform))
        }
    }

    private fun assertReverseTransformation(map: String, tests: String) {
        val stream = javaClass.getResourceAsStream(map)!!
        val transform = FileTransliteration(stream)
        val table = loadTable(tests)

        for (row in table) {
            val expectedCyrillic = row[0]
            val latinInput = row[1]
            val actual = WordTransformation.transformReverse(
                text = latinInput,
                transform = transform,
                autocorrect = true
            )
            if (transform.snapSeparator() == " ") {
                assertEquals(expectedCyrillic.lowercase(), actual.lowercase())
            } else {
                assertEquals("Failed reverse for '$latinInput'", expectedCyrillic, actual)
            }
        }
    }

    @Test
    fun testPassport_2010() {
        val time = measureTimeMillis {
            assertTransformation("/passport_2010.csv", "/passport_2010_test.csv")
        }
        println("measure: $time ms")
    }

    @Test
    fun testGeographic_1996() {
        val time = measureTimeMillis {
            assertTransformation("/geographic_1996.csv", "/geographic_1996_test.csv")
        }
        println("measure: $time ms")
    }

    @Test
    fun testAmerican() {
        val time = measureTimeMillis {
            assertTransformation("/american_1965.csv", "/american_1965_test.csv")
        }
        println("measure: $time ms")
    }

    @Test
    fun testManifest() {
        val time = measureTimeMillis {
            assertTransformation("/manifest.csv", "/manifest_test.csv")
        }
        println("measure: $time ms")
    }

    @Test
    fun testISO9_1995() {
        val time = measureTimeMillis {
            assertTransformation("/iso9_1995.csv", "/iso9_1995_test.csv")
        }
        println("measure: $time ms")
    }

    @Test
    fun testMorze() {
        val time = measureTimeMillis {
            assertTransformation("/morze.csv", "/morze_test.csv")
        }
        println("measure: $time ms")
    }

    @Test
    fun testReversePassport_2010() {
        assertReverseTransformation("/passport_2010.csv", "/passport_2010_test.csv")
    }

    @Test
    fun testReverseGeographic_1996() {
        assertReverseTransformation("/geographic_1996.csv", "/geographic_1996_test.csv")
    }

    @Test
    fun testReverseAmerican_1965() {
        assertReverseTransformation("/american_1965.csv", "/american_1965_test.csv")
    }

    @Test
    fun testReverseISO9_1995() {
        assertReverseTransformation("/iso9_1995.csv", "/iso9_1995_test.csv")
    }

    @Test
    fun testReverseManifest() {
        assertReverseTransformation("/manifest.csv", "/manifest_test.csv")
    }

    @Test
    fun testReverseMorze() {
        assertReverseTransformation("/morze.csv", "/morze_test.csv")
    }

    @Test
    fun testDirectionAutoDetection() {
        val passport = FileTransliteration(javaClass.getResourceAsStream("/passport_2010.csv")!!)
        val morze = FileTransliteration(javaClass.getResourceAsStream("/morze.csv")!!)

        assertEquals(
            TranslitDirection.CYRILLIC_TO_LATIN,
            WordTransformation.detectDirection("Доброго дня", passport)
        )
        assertEquals(
            TranslitDirection.LATIN_TO_CYRILLIC,
            WordTransformation.detectDirection("Dobroho dnia", passport)
        )
        assertEquals(
            TranslitDirection.LATIN_TO_CYRILLIC,
            WordTransformation.detectDirection("Kyїv", passport)
        )
        assertEquals(
            TranslitDirection.LATIN_TO_CYRILLIC,
            WordTransformation.detectDirection("-.- -. -.-- .... .-", morze)
        )
    }

    @Test
    fun testReversePhrasesAndAutocorrectToggle() {
        val passport = FileTransliteration(javaClass.getResourceAsStream("/passport_2010.csv")!!)

        assertEquals(
            "Привіт, як справи?",
            WordTransformation.transformReverse("Pryvit, yak spravy?", passport, autocorrect = true)
        )
        assertEquals(
            "Моя сім'я живе в Києві вже п'ять років.",
            WordTransformation.transformReverse(
                "Moia simia zhyve v Kyievi vzhe piat rokiv.",
                passport,
                autocorrect = true
            )
        )
        assertEquals(
            "Івано-Франківськ, Кам'янець-Подільський, Львів, Тернопіль",
            WordTransformation.transformReverse(
                "Ivano-Frankivsk, Kamianets-Podilskyi, Lviv, Ternopil",
                passport,
                autocorrect = true
            )
        )

        // With autocorrect disabled, raw transliteration does not insert soft sign or apostrophe
        assertEquals(
            "Лвів",
            WordTransformation.transformReverse("Lviv", passport, autocorrect = false)
        )
        assertEquals(
            "Львів",
            WordTransformation.transformReverse("Lviv", passport, autocorrect = true)
        )

        // 1-edit-distance typo correction
        assertEquals(
            "Комп'ютер",
            WordTransformation.transformReverse("Kompiutor", passport, autocorrect = true)
        )
        assertEquals(
            "Привіт",
            WordTransformation.transformReverse("Prvit", passport, autocorrect = true)
        )
    }
}
