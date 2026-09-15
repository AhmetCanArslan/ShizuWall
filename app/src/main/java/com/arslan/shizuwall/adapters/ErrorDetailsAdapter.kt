package com.arslan.shizuwall.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import com.arslan.shizuwall.utils.UiUtils

data class ErrorEntry(
    val appName: String,
    val packageName: String,
    val errorText: String,
    val userId: Int = 0
)

class ErrorDetailsAdapter(
    private val errorList: List<ErrorEntry>
) : RecyclerView.Adapter<ErrorDetailsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val errorText: TextView = itemView.findViewById(R.id.errorText)

        fun bind(entry: ErrorEntry) {
            UiUtils.loadAppIcon(appIcon, entry.packageName, entry.userId)
            appName.text = entry.appName
            errorText.text = entry.errorText
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_error_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(errorList[position])
    }

    override fun getItemCount(): Int = errorList.size
}
