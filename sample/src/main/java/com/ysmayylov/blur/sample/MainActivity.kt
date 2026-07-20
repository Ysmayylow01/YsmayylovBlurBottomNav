package com.ysmayylov.blur.sample

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ysmayylov.blur.bottomnav.NavItem
import com.ysmayylov.blur.sample.databinding.ActivityMainBinding

/**
 * Demonstrates [com.ysmayylov.blur.bottomnav.YsmayylovBlurBottomNavView] floating over a scrolling
 * list, so the live blur updates as colorful content passes behind the glass bar.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackgroundList()
        setupBottomNav()
    }

    private fun setupBackgroundList() {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = ColorAdapter()
        // Leave room so the last items can scroll above the floating bar.
        binding.recycler.clipToPadding = false
        binding.recycler.setPadding(0, 0, 0, dp(120))
    }

    private fun setupBottomNav() {
        val items = listOf(
            NavItem(id = "home",     icon = R.drawable.ic_home,     title = "Home"),
            NavItem(id = "search",   icon = R.drawable.ic_search,   title = "Search"),
            NavItem(id = "favorite", icon = R.drawable.ic_favorite, title = "Saved", badgeCount = 3),
            NavItem(id = "profile",  icon = R.drawable.ic_profile,  title = "Profile", badgeDot = true)
        )

        binding.bottomNav.setItems(items)
        binding.bottomNav.setOnItemSelectedListener { id ->
            Toast.makeText(this, "Selected: $id", Toast.LENGTH_SHORT).show()
            if (id == "favorite") binding.bottomNav.setBadgeCount("favorite", 0)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** Simple adapter that fills the screen with vivid gradient cards to show the blur off. */
    private class ColorAdapter : RecyclerView.Adapter<ColorAdapter.VH>() {

        private val colors = intArrayOf(
            Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
            Color.parseColor("#556270"), Color.parseColor("#C7F464"),
            Color.parseColor("#FFA400"), Color.parseColor("#5D5FEF"),
            Color.parseColor("#00C2A8"), Color.parseColor("#EF476F"),
            Color.parseColor("#118AB2"), Color.parseColor("#073B4C")
        )

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val label: TextView = view.findViewById(R.id.cardLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_color, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.itemView.setBackgroundColor(colors[position % colors.size])
            holder.label.text = "Card #${position + 1}"
        }

        override fun getItemCount() = 40
    }
}
