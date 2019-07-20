package bloo.ad.addbloo

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import bloo.ad.addbloo.db.Blocked
import kotlinx.android.synthetic.main.blockable_url.view.*

typealias OnUrlBlockedListener = (String, Boolean) -> Unit

class UrlListAdapter(var items : MutableList<Blocked>,
                     private val listener: OnUrlBlockedListener) : RecyclerView.Adapter<BlockableUrlView>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockableUrlView {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.blockable_url, parent,false)
        return BlockableUrlView(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: BlockableUrlView, position: Int) {
        val url = items[position]
        holder.view.switch1.setOnCheckedChangeListener(null)
        holder.view.url.text = url.host
        holder.view.switch1.isChecked = url.blocked
        holder.view.url.setTextColor(if (url.blocked) 0xFFE57373.toInt() else 0xFF81C784.toInt())
        holder.view.url.setBackgroundColor(Color.BLACK)
        holder.view.switch1.setOnCheckedChangeListener { _, isChecked ->
            listener.invoke(url.host, isChecked)
        }
    }

}

class BlockableUrlView(val view: View) : RecyclerView.ViewHolder(view)