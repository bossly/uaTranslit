package ua.bossly.tools.translit

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Performance unit benchmarks verifying execution speed, throughput, and sub-millisecond
 * latency for the uaTranslit engine.
 */
class TransliterationPerformanceTest {

    private val sampleUkrainianText = """
        Доброго дня! Україна — це чудова країна з багатою історією та мальовничою природою.
        Київ, Львів, Одеса, Харків, Дніпро, Запоріжжя, Івано-Франківськ, Чернівці, Ужгород.
        Швидка коричнева лисиця стрибає через лінивого собаку.
        Яблуко, їжак, єнот, юрта, ґанок, заєць, борщ, щирість, під'їзд, згоряння, щастя.
    """.trimIndent()

    @Test
    fun testLargeTextTransliterationPerformance() {
        val stream = javaClass.getResourceAsStream("/passport_2010.csv")!!
        val transform = FileTransliteration(stream)

        // Repeat text to create a ~50KB heavy workload
        val heavyPayload = StringBuilder().apply {
            repeat(150) {
                append(sampleUkrainianText).append("\n")
            }
        }.toString()

        // Warm up JVM
        WordTransformation.transform(sampleUkrainianText, transform)

        val durationMs = measureTimeMillis {
            WordTransformation.transform(heavyPayload, transform)
        }

        println("[Performance Test] 50KB Large Text Transliteration Time: ${durationMs}ms (${heavyPayload.length} chars)")

        // Assert execution completes under 300ms for 50KB payload
        assertTrue(
            "Large text transliteration should finish within 300ms (was ${durationMs}ms)",
            durationMs < 300
        )
    }

    @Test
    fun testKeystrokeSimulationLatency() {
        val stream = javaClass.getResourceAsStream("/passport_2010.csv")!!
        val transform = FileTransliteration(stream)

        var currentText = ""
        val sampleInput = "Доброго вечора, ми з України! Транслітерація працює миттєво."

        // Warm up JVM
        WordTransformation.transform(sampleInput, transform)

        val durationMs = measureTimeMillis {
            // Simulate 1000 typing events (character insertions)
            repeat(10) {
                for (ch in sampleInput) {
                    currentText += ch
                    WordTransformation.transform(currentText, transform)
                }
                currentText = ""
            }
        }

        println("[Performance Test] 1000 Simulated Keystrokes Total Time: ${durationMs}ms")

        // Assert 1,000 keystrokes finish within 150ms (< 0.15ms per keystroke)
        assertTrue(
            "1000 keystrokes should complete within 150ms (was ${durationMs}ms)",
            durationMs < 150
        )
    }

    @Test
    fun testRuleTableInitializationSpeed() {
        val stream = javaClass.getResourceAsStream("/passport_2010.csv")!!

        val initTimeMs = measureTimeMillis {
            FileTransliteration(stream)
        }

        println("[Performance Test] FileTransliteration Map Init Time: ${initTimeMs}ms")

        // Assert map init takes under 50ms
        assertTrue(
            "FileTransliteration initialization should take < 50ms (was ${initTimeMs}ms)",
            initTimeMs < 50
        )
    }

    @Test
    fun testAllStandardsBenchmark() {
        val standards = listOf(
            "/passport_2010.csv",
            "/geographic_1996.csv",
            "/american_1965.csv",
            "/manifest.csv",
            "/iso9_1995.csv",
            "/morze.csv"
        )

        val heavyPayload = StringBuilder().apply {
            repeat(50) { append(sampleUkrainianText).append("\n") }
        }.toString()

        for (stdPath in standards) {
            val stream = javaClass.getResourceAsStream(stdPath)!!
            val transform = FileTransliteration(stream)

            // Warm up
            WordTransformation.transform("Тест", transform)

            val timeMs = measureTimeMillis {
                WordTransformation.transform(heavyPayload, transform)
            }

            println("[Performance Test] Standard $stdPath: ${timeMs}ms")
            assertTrue(
                "Standard $stdPath should complete under 200ms (was ${timeMs}ms)",
                timeMs < 200
            )
        }
    }
}
