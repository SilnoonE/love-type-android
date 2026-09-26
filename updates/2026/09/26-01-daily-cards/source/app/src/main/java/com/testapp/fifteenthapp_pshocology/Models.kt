package com.testapp.fifteenthapp_pshocology

enum class ScoringType { EMOTIONAL, CLINGY, LEADING, FREE }

data class Answer(val text: String, val type: ScoringType)

data class Question(
    val id: Int,
    val title: String,
    val category: String,
    val answers: List<Answer>
)

data class ResultType(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val tags: List<String>,
    val pros: List<String>,
    val cons: List<String>,
    val matchPartner: String,
    val tip: String,
    val shareMsg: String,
    val scores: Map<ScoringType, Int>
)
