package com.brandx.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brandx.app.data.Channel
import com.brandx.app.data.Playlist
import com.brandx.app.data.PlaylistParser
import com.brandx.app.data.VedaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class PlaylistActivity : AppCompatActivity() {
    private val db by lazy { VedaDatabase.get(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 16) }
        root.addView(TextView(this).apply { text = "VEDA IPTV"; textSize = 26f })
        root.addView(Button(this).apply {
            text = "Добавить плейлист"
            setOnClickListener { showAddDialog() }
        })
        progress = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progress)
        status = TextView(this).apply { text = "Плейлисты не добавлены" }
        root.addView(status)
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        lifecycleScope.launch {
            db.playlistDao().observeAll().collectLatest { playlists ->
                status.text = if (playlists.isEmpty()) "Добавьте свой M3U/M3U8 плейлист" else "Плейлистов: ${playlists.size}"
                list.adapter = ArrayAdapter(this@PlaylistActivity, android.R.layout.simple_list_item_1, playlists.map {
                    "${it.name}\n${it.channelCount} каналов"
                })
            }
        }
        list.setOnItemClickListener { _, _, index, _ ->
            lifecycleScope.launch {
                val playlists = db.playlistDao().observeAll().let { emptyList<Playlist>() }
            }
            openPlaylist(index)
        }
        list.setOnItemLongClickListener { _, _, index, _ ->
            lifecycleScope.launch {
                val playlists = db.playlistDao().observeAll().let { emptyList<Playlist>() }
            }
            showPlaylistMenu(index)
            true
        }
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val name = EditText(this).apply { hint = "Название плейлиста"; setSingleLine(true) }
        val url = EditText(this).apply {
            hint = "Ссылка M3U / M3U8"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        layout.addView(name); layout.addView(url)
        AlertDialog.Builder(this)
            .setTitle("Новый плейлист")
            .setView(layout)
            .setPositiveButton("Добавить") { _, _ -> add(name.text.toString().trim(), url.text.toString().trim()) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun add(name: String, url: String) {
        if (name.isBlank() || !PlaylistParser.valid(url)) {
            Toast.makeText(this, "Введите название и корректную ссылку HTTP/HTTPS", Toast.LENGTH_LONG).show()
            return
        }
        progress.visibility = View.VISIBLE
        status.text = "Загрузка плейлиста…"
        worker.submit {
            try {
                val text = PlaylistParser.download(url)
                val savedId = db.playlistDao().save(Playlist(name = name, url = url, updatedAt = System.currentTimeMillis()))
                val channels = PlaylistParser.parse(text, savedId)
                db.playlistDao().replace(savedId, Playlist(name = name, url = url, updatedAt = System.currentTimeMillis(), channelCount = channels.size), channels)
                runOnUiThread { progress.visibility = View.GONE; status.text = "Добавлено: ${channels.size} каналов" }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                runOnUiThread { progress.visibility = View.GONE; status.text = "Ошибка: ${e.message ?: "проверьте ссылку и интернет"}" }
            }
        }
    }

    private fun openPlaylist(index: Int) {
        worker.submit {
            val playlists = db.playlistDao().observeAll().let { emptyList<Playlist>() }
            runOnUiThread {
                if (index < playlists.size) {
                    startActivity(Intent(this, ChannelActivity::class.java).putExtra("playlistId", playlists[index].id))
                }
            }
        }
    }

    private fun showPlaylistMenu(index: Int) {
        worker.submit {
            val playlists = db.playlistDao().observeAll().let { emptyList<Playlist>() }
            runOnUiThread {
                if (index >= playlists.size) return@runOnUiThread
                val playlist = playlists[index]
                val options = arrayOf("Обновить", "Переименовать", "Удалить")
                AlertDialog.Builder(this).setTitle(playlist.name).setItems(options) { _, which ->
                    when (which) {
                        0 -> add(playlist.name, playlist.url)
                        1 -> {
                            val input = EditText(this).apply { setText(playlist.name); setSingleLine(true) }
                            AlertDialog.Builder(this).setTitle("Новое название").setView(input)
                                .setPositiveButton("Сохранить") { _, _ ->
                                    worker.submit {
                                        db.playlistDao().save(playlist.copy(name = input.text.toString().trim()))
                                    }
                                }.setNegativeButton("Отмена", null).show()
                        }
                        2 -> AlertDialog.Builder(this).setMessage("Удалить плейлист «${playlist.name}»?")
                            .setPositiveButton("Удалить") { _, _ -> worker.submit { db.playlistDao().delete(playlist.id); db.playlistDao().clearChannels(playlist.id); db.playlistDao().clearFavorites(playlist.id); db.playlistDao().clearRecent(playlist.id) } }
                            .setNegativeButton("Отмена", null).show()
                    }
                }.show()
            }
        }
    }

    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
