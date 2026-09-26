package com.testapp.fifteenthapp_pshocology

data class DailyOption(
    val id: String, // "A", "B", "C", "D"
    val text: String,
    val resultTitle: String,
    val interpretation: String,
    val conversation: String
)

data class DailyQuestion(
    val id: String, // e.g. "Q01"
    val category: String, // "데이트", "연락", "애정표현", "갈등", "거리감", "가치관"
    val illustrationKey: String, // "DATE", "CONTACT", "AFFECTION", "CONFLICT", "SPACE", "VALUES"
    val prompt: String,
    val options: List<DailyOption>
)

data class DailyCardRecord(
    val dateKey: String, // "yyyy-MM-dd"
    val questionId: String,
    val selectedOptionId: String,
    val questionSnapshot: DailyQuestion,
    val isFavorite: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    val selectedOption: DailyOption?
        get() = questionSnapshot.options.find { it.id == selectedOptionId }
}
