package ua.bossly.tools.translit

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
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
    fun testMorze() {
        val time = measureTimeMillis {
            assertTransformation("/morze.csv", "/morze_test.csv")
        }
        println("measure: $time ms")
    }

}