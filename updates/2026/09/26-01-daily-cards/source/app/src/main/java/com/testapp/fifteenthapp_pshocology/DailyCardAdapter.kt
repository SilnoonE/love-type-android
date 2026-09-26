package com.testapp.fifteenthapp_pshocology

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DailyCardAdapter(
    private val onItemClick: (DailyCardRecord) -> Unit,
    private val onFavoriteToggle: (DailyCardRecord) -> Unit
) : RecyclerView.Adapter<DailyCardAdapter.DailyCardViewHolder>() {

    private val items = mutableListOf<DailyCardRecord>()

    fun submitList(newList: List<DailyCardRecord>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DailyCardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_daily_card, parent, false)
        return DailyCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: DailyCardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DailyCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvChip: TextView = itemView.findViewById(R.id.tv_item_chip)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_item_date)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_item_favorite)
        private val ivIllustration: ImageView = itemView.findViewById(R.id.iv_item_illustration)
        private val tvResultTitle: TextView = itemView.findViewById(R.id.tv_item_result_title)
        private val tvSelectedOption: TextView = itemView.findViewById(R.id.tv_item_selected_option)

        fun bind(record: DailyCardRecord) {
            tvChip.text = record.questionSnapshot.category
            tvDate.text = record.dateKey

            val illuResId = DailyCardShareRenderer.getIllustrationResId(record.questionSnapshot.illustrationKey)
            ivIllustration.setImageResource(illuResId)

            val option = record.selectedOption
            tvResultTitle.text = option?.resultTitle ?: ""
            tvSelectedOption.text = "답변: ${option?.text ?: ""}"

            if (record.isFavorite) {
                btnFavorite.setImageResource(R.drawable.ic_favorite_filled)
                btnFavorite.contentDescription = "즐겨찾기 해제"
            } else {
                btnFavorite.setImageResource(R.drawable.ic_favorite_border)
                btnFavorite.contentDescription = "즐겨찾기 추가"
            }

            btnFavorite.setOnClickListener {
                onFavoriteToggle(record)
            }

            itemView.setOnClickListener {
                onItemClick(record)
            }
        }
    }
}
