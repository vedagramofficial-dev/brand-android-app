package com.brandx.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brandx.app.data.Channel
import com.brandx.app.data.Favorite
import com.brandx.app.data.RecentChannel
import com.brandx.app.data.VedaDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChannelActivity : AppCompatActivity() {
    private val db by lazy { VedaDatabase.get(this) }
    private var playlistId = 0L
    private var channels = emptyList<Channel>()
    private var favorites = emptySet<String>()
    private var recent = emptySet<String>()
    private var visible = emptyList<Channel>()
    private var groups = listOf("Все каналы", "Избранное", "Недавние")
    private lateinit var categories: Spinner
    private lateinit var search: EditText
    private lateinit var list: ListView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        playlistId = intent.getLongExtra("playlistId", 0L)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 8) }
        root.addView(TextView(this).apply { text = intent.getStringExtra("name") ?: "Каналы"; textSize = 22f })
        categories = Spinner(this)
        root.addView(categories)
        search = EditText(this).apply { hint = "Поиск канала"; setSingleLine(true) }
        root.addView(search)
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { filter() }
        })
        categories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { filter() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        list.setOnItemClickListener { _, _, index, _ -> visible.getOrNull(index)?.let(::open) }
        list.setOnItemLongClickListener { _, _, index, _ ->
            visible.getOrNull(index)?.let { channel ->
                lifecycleScope.launch {
                    if (channel.url in favorites) db.channelDao().removeFavorite(playlistId, channel.url)
                    else db.channelDao().addFavorite(Favorite(playlistId, channel.url, System.currentTimeMillis()))
                    favorites = db.channelDao().observeFavorites(playlistId).first().map { it.url }.toSet()
                    filter()
                    Toast.makeText(this@ChannelActivity, if (channel.url in favorites) "Добавлено в избранное" else "Удалено из избранного", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        lifecycleScope.launch {
            db.channelDao().observeChannels(playlistId).collectLatest { items ->
                channels = items
                groups = listOf("Все каналы", "Избранное", "Недавние") + items.map { it.category }.distinct().sorted()
                categories.adapter = ArrayAdapter(this@ChannelActivity, android.R.layout.simple_spinner_dropdown_item, groups)
                filter()
            }
        }
        lifecycleScope.launch {
            db.channelDao().observeFavorites(playlistId).collectLatest { items ->
                favorites = items.map { it.url }.toSet(); filter()
            }
        }
        lifecycleScope.launch {
            db.channelDao().observeRecent(playlistId).collectLatest { items ->
                recent = items.map { it.url }.toSet(); filter()
            }
        }
    }

    private fun filter() {
        val group = groups.getOrElse(categories.selectedItemPosition) { "Все каналы" }
        val query = search.text.toString().trim()
        visible = channels.filter {
            val inGroup = when (group) {
                "Все каналы" -> true
                "Избранное" -> it.url in favorites
                "Недавние" -> it.url in recent
                else -> it.category == group
            }
            inGroup && it.name.contains(query, true)
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, visible.map {
            (if (it.url in favorites) "★ " else "") + it.name
        })
    }

    private fun open(channel: Channel) {
        lifecycleScope.launch {
            db.channelDao().addRecent(RecentChannel(playlistId, channel.url, System.currentTimeMillis()))
            startActivity(
                Intent(this@ChannelActivity, PlayerActivity::class.java)
                    .putExtra("playlistId", playlistId)
                    .putExtra("url", channel.url)
                    .putExtra("name", channel.name)
                    .putExtra("hls", channel.hls)
            )
        }
    }
}
