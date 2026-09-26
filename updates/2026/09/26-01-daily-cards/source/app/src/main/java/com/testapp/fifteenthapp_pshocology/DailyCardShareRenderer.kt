package com.testapp.fifteenthapp_pshocology

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object DailyCardShareRenderer {

    private val executor = Executors.newSingleThreadExecutor()

    fun shareRecord(context: Context, record: DailyCardRecord) {
        val option = record.selectedOption
        if (option == null) {
            Toast.makeText(context, "이미지를 준비하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Inflate view on main thread
        val view = LayoutInflater.from(context).inflate(R.layout.view_share_daily_card, null)

        view.findViewById<TextView>(R.id.tv_share_date).text = record.dateKey
        view.findViewById<TextView>(R.id.tv_share_chip).text = record.questionSnapshot.category

        val illuResId = getIllustrationResId(record.questionSnapshot.illustrationKey)
        view.findViewById<ImageView>(R.id.iv_share_illustration).setImageResource(illuResId)

        view.findViewById<TextView>(R.id.tv_share_result_title).text = option.resultTitle
        view.findViewById<TextView>(R.id.tv_share_interpretation).text = option.interpretation
        view.findViewById<TextView>(R.id.tv_share_conversation).text = "“${option.conversation}”"

        view.findViewById<TextView>(R.id.tv_share_question_prompt).text = record.questionSnapshot.prompt
        view.findViewById<TextView>(R.id.tv_share_selected_option).text = option.text

        // 2. Measure & Layout
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        val measuredWidth = view.measuredWidth
        val measuredHeight = view.measuredHeight
        view.layout(0, 0, measuredWidth, measuredHeight)

        // 3. Draw to Bitmap
        val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        // 4. Save and share on background thread
        executor.execute {
            try {
                val cachePath = File(context.cacheDir, "images")
                if (!cachePath.exists()) cachePath.mkdirs()

                val file = File(cachePath, "daily_card_share.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareText = "오늘 나의 선택은 ‘${option.resultTitle}’! 너라면 어떤 답을 골랐을까?"

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    clipData = ClipData.newRawUri("daily_card", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(shareIntent, "오늘의 카드 공유하기")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)

            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "이미지를 준비하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getIllustrationResId(key: String): Int {
        return when (key) {
            "DATE" -> R.drawable.ic_illustration_date
            "CONTACT" -> R.drawable.ic_illustration_contact
            "AFFECTION" -> R.drawable.ic_illustration_affection
            "CONFLICT" -> R.drawable.ic_illustration_conflict
            "SPACE" -> R.drawable.ic_illustration_space
            "VALUES" -> R.drawable.ic_illustration_values
            else -> R.drawable.ic_illustration_date
        }
    }
}
