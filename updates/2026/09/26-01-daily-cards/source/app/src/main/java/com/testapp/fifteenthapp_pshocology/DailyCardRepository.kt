package com.testapp.fifteenthapp_pshocology

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class DailyCardRepository(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val PREF_NAME = "daily_love_cards_v1"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_ANCHOR_DATE = "daily_anchor_date"
        private const val KEY_RECORDS_JSON = "records_json"
        private const val CURRENT_SCHEMA_VERSION = 1
    }

    @Synchronized
    fun getOrCreateAnchorDate(todayDateKey: String): String {
        val existing = prefs.getString(KEY_ANCHOR_DATE, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        prefs.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putString(KEY_ANCHOR_DATE, todayDateKey)
            .apply()
        return todayDateKey
    }



    @Synchronized
    fun getAllRecords(): List<DailyCardRecord> {
        val jsonStr = prefs.getString(KEY_RECORDS_JSON, null) ?: return emptyList()
        val list = mutableListOf<DailyCardRecord>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val record = parseRecordFromJson(jsonObj)
                    if (record != null) {
                        list.add(record)
                    }
                } catch (e: Exception) {
                    // Isolate corrupted record
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Return sorted by dateKey descending (newest first)
        return list.sortedByDescending { it.dateKey }
    }

    @Synchronized
    fun getRecordByDate(dateKey: String): DailyCardRecord? {
        return getAllRecords().find { it.dateKey == dateKey }
    }

    @Synchronized
    fun getTodayRecord(todayDateKey: String): DailyCardRecord? {
        return getRecordByDate(todayDateKey)
    }

    fun upsertAnswer(
        dateKey: String,
        questionSnapshot: DailyQuestion,
        selectedOptionId: String,
        onResult: (Boolean) -> Unit
    ) {
        executor.execute {
            val success = try {
                synchronized(this) {
                    val currentRecords = getAllRecords().toMutableList()
                    val existingIndex = currentRecords.indexOfFirst { it.dateKey == dateKey }
                    val now = System.currentTimeMillis()

                    if (existingIndex >= 0) {
                        val existing = currentRecords[existingIndex]
                        val updated = existing.copy(
                            questionId = questionSnapshot.id,
                            selectedOptionId = selectedOptionId,
                            questionSnapshot = questionSnapshot,
                            updatedAtEpochMillis = now
                        )
                        currentRecords[existingIndex] = updated
                    } else {
                        val newRecord = DailyCardRecord(
                            dateKey = dateKey,
                            questionId = questionSnapshot.id,
                            selectedOptionId = selectedOptionId,
                            questionSnapshot = questionSnapshot,
                            isFavorite = false,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now
                        )
                        currentRecords.add(newRecord)
                    }

                    saveRecordsToJson(currentRecords)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            onResult(success)
        }
    }

    fun setFavorite(dateKey: String, isFavorite: Boolean, onResult: (Boolean) -> Unit) {
        executor.execute {
            val success = try {
                synchronized(this) {
                    val currentRecords = getAllRecords().toMutableList()
                    val index = currentRecords.indexOfFirst { it.dateKey == dateKey }
                    if (index >= 0) {
                        val existing = currentRecords[index]
                        currentRecords[index] = existing.copy(isFavorite = isFavorite)
                        saveRecordsToJson(currentRecords)
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            onResult(success)
        }
    }

    private fun saveRecordsToJson(records: List<DailyCardRecord>): Boolean {
        val jsonArray = JSONArray()
        records.forEach { record ->
            jsonArray.put(recordToJson(record))
        }
        return prefs.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putString(KEY_RECORDS_JSON, jsonArray.toString())
            .commit()
    }

    private fun recordToJson(record: DailyCardRecord): JSONObject {
        val obj = JSONObject()
        obj.put("dateKey", record.dateKey)
        obj.put("questionId", record.questionId)
        obj.put("selectedOptionId", record.selectedOptionId)
        obj.put("isFavorite", record.isFavorite)
        obj.put("createdAtEpochMillis", record.createdAtEpochMillis)
        obj.put("updatedAtEpochMillis", record.updatedAtEpochMillis)

        val qObj = JSONObject()
        qObj.put("id", record.questionSnapshot.id)
        qObj.put("category", record.questionSnapshot.category)
        qObj.put("illustrationKey", record.questionSnapshot.illustrationKey)
        qObj.put("prompt", record.questionSnapshot.prompt)

        val optsArr = JSONArray()
        record.questionSnapshot.options.forEach { opt ->
            val optObj = JSONObject()
            optObj.put("id", opt.id)
            optObj.put("text", opt.text)
            optObj.put("resultTitle", opt.resultTitle)
            optObj.put("interpretation", opt.interpretation)
            optObj.put("conversation", opt.conversation)
            optsArr.put(optObj)
        }
        qObj.put("options", optsArr)

        obj.put("questionSnapshot", qObj)
        return obj
    }

    private fun parseRecordFromJson(obj: JSONObject): DailyCardRecord? {
        val dateKey = obj.getString("dateKey")
        val questionId = obj.getString("questionId")
        val selectedOptionId = obj.getString("selectedOptionId")
        val isFavorite = obj.optBoolean("isFavorite", false)
        val createdAt = obj.optLong("createdAtEpochMillis", System.currentTimeMillis())
        val updatedAt = obj.optLong("updatedAtEpochMillis", System.currentTimeMillis())

        val qObj = obj.getJSONObject("questionSnapshot")
        val qId = qObj.getString("id")
        val category = qObj.getString("category")
        val illustrationKey = qObj.getString("illustrationKey")
        val prompt = qObj.getString("prompt")

        val optsArr = qObj.getJSONArray("options")
        val optionsList = mutableListOf<DailyOption>()
        for (i in 0 until optsArr.length()) {
            val optObj = optsArr.getJSONObject(i)
            optionsList.add(
                DailyOption(
                    id = optObj.getString("id"),
                    text = optObj.getString("text"),
                    resultTitle = optObj.getString("resultTitle"),
                    interpretation = optObj.getString("interpretation"),
                    conversation = optObj.getString("conversation")
                )
            )
        }

        val question = DailyQuestion(
            id = qId,
            category = category,
            illustrationKey = illustrationKey,
            prompt = prompt,
            options = optionsList
        )

        return DailyCardRecord(
            dateKey = dateKey,
            questionId = questionId,
            selectedOptionId = selectedOptionId,
            questionSnapshot = question,
            isFavorite = isFavorite,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt
        )
    }
}
