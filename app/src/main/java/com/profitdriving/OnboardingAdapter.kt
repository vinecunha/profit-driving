package com.profitdriving

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(
    private val items: List<OnboardingItem>
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageIcon: ImageView = itemView.findViewById(R.id.imageIcon)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDescription: TextView = itemView.findViewById(R.id.textDescription)
        private val benefitsList: LinearLayout = itemView.findViewById(R.id.benefitsList)
        private val textPrivacyBadge: TextView = itemView.findViewById(R.id.textPrivacyBadge)

        fun bind(item: OnboardingItem) {
            imageIcon.setImageResource(item.imageRes)
            textTitle.text = item.title
            textDescription.text = item.description
            itemView.setBackgroundColor(
                ContextCompat.getColor(itemView.context, item.backgroundColor)
            )
            benefitsList.visibility = if (item.showBenefits) View.VISIBLE else View.GONE
            textPrivacyBadge.visibility = if (item.showPrivacyBadge) View.VISIBLE else View.GONE
        }
    }
}
