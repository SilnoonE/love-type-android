package com.testapp.fifteenthapp_pshocology

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DailyQuestionScheduler(
    private val repository: DailyCardRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    val fixedOrder: List<String> = listOf(
        "Q01", "Q06", "Q11", "Q16", "Q21", "Q26",
        "Q02", "Q07", "Q12", "Q17", "Q22", "Q27",
        "Q03", "Q08", "Q13", "Q18", "Q23", "Q28",
        "Q04", "Q09", "Q14", "Q19", "Q24", "Q29",
        "Q05", "Q10", "Q15", "Q20", "Q25", "Q30"
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getTodayDateKey(): String {
        return LocalDate.now(clock.withZone(zoneId)).format(dateFormatter)
    }

    fun getQuestionForDate(dateKey: String): DailyQuestion {
        // Priority to saved record snapshot if available for that date
        val savedRecord = repository.getRecordByDate(dateKey)
        if (savedRecord != null) {
            return savedRecord.questionSnapshot
        }

        val anchorDateStr = repository.getOrCreateAnchorDate(dateKey)
        val anchorDate = try {
            LocalDate.parse(anchorDateStr, dateFormatter)
        } catch (e: Exception) {
            LocalDate.parse(dateKey, dateFormatter)
        }

        val targetDate = try {
            LocalDate.parse(dateKey, dateFormatter)
        } catch (e: Exception) {
            LocalDate.now(clock.withZone(zoneId))
        }

        val days = ChronoUnit.DAYS.between(anchorDate, targetDate)
        val index = Math.floorMod(days, 30L).toInt()
        val questionId = fixedOrder[index]

        return DailyQuestionData.getQuestion(questionId)
    }
}
