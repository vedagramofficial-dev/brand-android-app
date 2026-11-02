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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class PlaylistActivity : AppCompatActivity() {
    private val db by lazy { VedaDatabase.get(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private var playlists = emptyList<Playlist>()
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 16) }
        root.addView(TextView(this).apply { text = "VEDA IPTV"; textSize = 26f })
        root.addView(Button(this).apply { text = "Добавить плейлист"; setOnClickListener { showAddDialog() } })
        progress = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progress)
        status = TextView(this).apply { text = "Добавьте свой M3U/M3U8 плейлист" }
        root.addView(status)
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        lifecycleScope.launch {
            db.playlistDao().observeAll().collectLatest { items ->
                playlists = items
                status.text = if (items.isEmpty()) "Добавьте свой M3U/M3U8 плейлист" else "Плейлистов: ${items.size}"
                list.adapter = ArrayAdapter(this@PlaylistActivity, android.R.layout.simple_list_item_1, items.map { "${it.name}\n${it.channelCount} каналов" })
            }
        }
        list.setOnItemClickListener { _, _, index, _ ->
            playlists.getOrNull(index)?.let {
                startActivity(Intent(this, ChannelActivity::class.java).putExtra("playlistId", it.id).putExtra("name", it.name))
            }
        }
        list.setOnItemLongClickListener { _, _, index, _ ->
            playlists.getOrNull(index)?.let { showMenu(it) }
            true
        }
    }

    private fun showAddDialog(existing: Playlist? = null) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val name = EditText(this).apply { hint = "Название плейлиста"; setSingleLine(true); setText(existing?.name ?: "") }
        val url = EditText(this).apply {
            hint = "Ссылка M3U / M3U8"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true); setText(existing?.url ?: "")
        }
        layout.addView(name); layout.addView(url)
        AlertDialog.Builder(this).setTitle(if (existing == null) "Новый плейлист" else "Изменить плейлист")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ -> save(existing, name.text.toString().trim(), url.text.toString().trim()) }
            .setNegativeButton("Отмена", null).show()
    }

    private fun save(existing: Playlist?, name: String, url: String) {
        if (name.isBlank() || !PlaylistParser.valid(url)) {
            Toast.makeText(this, "Введите название и корректную ссылку HTTP/HTTPS", Toast.LENGTH_LONG).show()
            return
        }
        progress.visibility = View.VISIBLE
        status.text = "Загрузка плейлиста…"
        worker.submit {
            try {
                val text = PlaylistParser.download(url)
                val id = existing?.id ?: 0L
                val playlist = Playlist(id = id, name = name, url = url, updatedAt = System.currentTimeMillis())
                val savedId = db.playlistDao().save(playlist)
                val channels = PlaylistParser.parse(text, savedId)
                db.playlistDao().replace(savedId, playlist.copy(id = savedId, channelCount = channels.size), channels)
                runOnUiThread { progress.visibility = View.GONE; status.text = "Готово: ${channels.size} каналов" }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                runOnUiThread { progress.visibility = View.GONE; status.text = "Ошибка: ${e.message ?: "проверьте ссылку и интернет"}" }
            }
        }
    }

    private fun showMenu(playlist: Playlist) {
        AlertDialog.Builder(this).setTitle(playlist.name)
            .setItems(arrayOf("Обновить", "Переименовать / изменить ссылку", "Удалить")) { _, which ->
                when (which) {
                    0 -> save(playlist, playlist.name, playlist.url)
                    1 -> showAddDialog(playlist)
                    2 -> AlertDialog.Builder(this).setMessage("Удалить «${playlist.name}» и его каналы?")
                        .setPositiveButton("Удалить") { _, _ -> worker.submit {
                            db.playlistDao().delete(playlist.id)
                            db.playlistDao().clearChannels(playlist.id)
                            db.playlistDao().clearFavorites(playlist.id)
                            db.playlistDao().clearRecent(playlist.id)
                        } }.setNegativeButton("Отмена", null).show()
                }
            }.show()
    }

    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
