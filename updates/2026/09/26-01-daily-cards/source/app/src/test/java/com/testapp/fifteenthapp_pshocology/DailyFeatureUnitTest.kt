package com.testapp.fifteenthapp_pshocology

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DailyFeatureUnitTest {

    @Test
    fun testAll30QuestionsAnd120OptionsIntegrity() {
        val questions = DailyQuestionData.questions
        assertEquals(30, questions.size)

        val questionIds = questions.map { it.id }.toSet()
        assertEquals(30, questionIds.size)

        val validIllustrationKeys = setOf("DATE", "CONTACT", "AFFECTION", "CONFLICT", "SPACE", "VALUES")
        val validCategories = setOf("데이트", "연락", "애정표현", "갈등", "거리감", "가치관")

        var totalOptionsCount = 0

        questions.forEach { q ->
            assertTrue("Question ID should start with Q", q.id.startsWith("Q"))
            assertTrue("Category invalid: ${q.category}", validCategories.contains(q.category))
            assertTrue("Illustration key invalid: ${q.illustrationKey}", validIllustrationKeys.contains(q.illustrationKey))
            assertTrue("Prompt should not be empty", q.prompt.isNotBlank())
            assertEquals("Each question must have 4 options", 4, q.options.size)

            val optionIds = q.options.map { it.id }
            assertEquals(listOf("A", "B", "C", "D"), optionIds)

            q.options.forEach { opt ->
                totalOptionsCount++
                assertTrue("Option text should not be blank", opt.text.isNotBlank())
                assertTrue("Result title should not be blank", opt.resultTitle.isNotBlank())
                assertTrue("Interpretation should not be blank", opt.interpretation.isNotBlank())
                assertTrue("Conversation should not be blank", opt.conversation.isNotBlank())
                assertFalse("Result title should not contain prefix", opt.resultTitle.contains("오늘의 선택:"))
            }
        }

        assertEquals(120, totalOptionsCount)
    }

    @Test
    fun testFixedOrderAndScheduleCalculation() {
        val fixedOrder = listOf(
            "Q01", "Q06", "Q11", "Q16", "Q21", "Q26",
            "Q02", "Q07", "Q12", "Q17", "Q22", "Q27",
            "Q03", "Q08", "Q13", "Q18", "Q23", "Q28",
            "Q04", "Q09", "Q14", "Q19", "Q24", "Q29",
            "Q05", "Q10", "Q15", "Q20", "Q25", "Q30"
        )

        assertEquals(30, fixedOrder.size)
        assertEquals(30, fixedOrder.toSet().size)

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val anchorDate = LocalDate.parse("2025-01-01", formatter)

        // Day 0 -> Q01
        var days = ChronoUnit.DAYS.between(anchorDate, LocalDate.parse("2025-01-01", formatter))
        var index = Math.floorMod(days, 30L).toInt()
        assertEquals("Q01", fixedOrder[index])

        // Day 1 -> Q06
        days = ChronoUnit.DAYS.between(anchorDate, LocalDate.parse("2025-01-02", formatter))
        index = Math.floorMod(days, 30L).toInt()
        assertEquals("Q06", fixedOrder[index])

        // Day 30 -> Q01 (Cycle repeats)
        days = ChronoUnit.DAYS.between(anchorDate, LocalDate.parse("2025-01-31", formatter))
        index = Math.floorMod(days, 30L).toInt()
        assertEquals("Q01", fixedOrder[index])

        // Negative days test (e.g. anchorDate in future or date in past)
        days = ChronoUnit.DAYS.between(anchorDate, LocalDate.parse("2024-12-31", formatter))
        index = Math.floorMod(days, 30L).toInt()
        assertEquals("Q30", fixedOrder[index])
    }
}
