package com.brandx.app

import android.os.Bundle
import android.net.Uri
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : AppCompatActivity() {
    private data class Channel(val name: String, val url: String, val hls: Boolean = false)
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var generation = 0
    private var player: ExoPlayer? = null
    private lateinit var video: PlayerView
    private lateinit var status: TextView
    private lateinit var address: EditText
    private lateinit var list: ListView
    private lateinit var busy: ProgressBar
    private var channels: List<Channel> = emptyList()
    private var selected: Channel? = null
    private var position = 0L
    private fun valid(url: String): Boolean = try {
        val u = URI(url)
        (u.scheme.equals("https", true) || u.scheme.equals("http", true)) && !u.host.isNullOrBlank() && u.userInfo == null
    } catch (_: Exception) { false }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        root.addView(TextView(this).apply { text = "VEDA IPTV"; textSize = 24f })
        address = EditText(this).apply {
            hint = "M3U / M3U8 URL (https://...)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(state?.getString("address") ?: "")
        }
        root.addView(address)
        root.addView(Button(this).apply {
            text = "Load playlist"
            setOnClickListener { load(address.text.toString().trim()) }
        })
        busy = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(busy)
        status = TextView(this).apply { text = "Enter your playlist URL. HTTP is not encrypted." }
        root.addView(status)
        video = PlayerView(this)
        root.addView(video, LinearLayout.LayoutParams(-1, (220 * resources.displayMetrics.density).toInt()))
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnItemClickListener { _, _, index, _ ->
            selected = channels[index]
            position = 0L
            play()
        }
        setContentView(root)
        state?.getString("stream")?.let {
            selected = Channel(state.getString("title") ?: "Stream", it, state.getBoolean("hls"))
            position = state.getLong("position")
        }
    }

    private fun load(url: String) {
        if (!valid(url)) { status.text = "Enter a valid HTTP/HTTPS URL without embedded credentials"; return }
        generation++
        val current = generation
        task?.cancel(true)
        busy.visibility = View.VISIBLE
        status.text = "Loading..."
        task = executor.submit {
            try {
                var next = url
                var content = ""
                var base = url
                var fetched = false
                val deadline = System.nanoTime() + 45_000_000_000L
                for (hop in 0..5) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    if (!valid(next)) error("Unsupported redirect")
                    val connection = URL(next).openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", "VEDA-IPTV/1.1")
                    try {
                        val code = connection.responseCode
                        if (code in listOf(301, 302, 303, 307, 308)) {
                            val location = connection.getHeaderField("Location") ?: error("Missing redirect")
                            next = URI(next).resolve(location).toString()
                            continue
                        }
                        if (code !in 200..299) error("HTTP $code")
                        base = next
                        connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                                if (System.nanoTime() > deadline) error("Playlist timeout")
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 5 * 1024 * 1024) error("Playlist exceeds 5 MB")
                                output.write(buffer, 0, count)
                            }
                            content = output.toString("UTF-8").removePrefix("\uFEFF")
                        }
                        fetched = true
                        break
                    } finally { connection.disconnect() }
                }
                if (!fetched) error("Too many redirects")
                if (!content.trimStart().startsWith("#EXTM3U")) error("Not an M3U playlist")
                val parsed = mutableListOf<Channel>()
                if (content.lineSequence().any { it.trim().startsWith("#EXT-X-") }) {
                    parsed.add(Channel("HLS stream", base, true))
                } else {
                    var title = ""
                    for (raw in content.lineSequence()) {
                        val line = raw.trim()
                        if (line.startsWith("#EXTINF:")) title = line.substringAfter(',', "").trim()
                        else if (line.isNotBlank() && !line.startsWith("#")) {
                            val link = try { URI(base).resolve(line).toString() } catch (_: Exception) { "" }
                            if (valid(link)) parsed.add(Channel(title.ifBlank { "Channel ${parsed.size + 1}" }, link))
                            title = ""
                            if (parsed.size >= 20000) break
                        }
                    }
                }
                if (parsed.isEmpty()) error("No supported HTTP/HTTPS channels found")
                runOnUiThread {
                    if (!isDestroyed && current == generation) {
                        channels = parsed
                        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, parsed.map { it.name })
                        status.text = "Channels: ${parsed.size}. Select one to play."
                        busy.visibility = View.GONE
                    }
                }
            } catch (_: InterruptedException) {
                // Superseded by another request or activity destruction.
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isDestroyed && current == generation) {
                        busy.visibility = View.GONE
                        status.text = "Playlist failed. Check URL, network and provider access."
                    }
                }
            }
        }
    }

    private fun play() {
        val channel = selected ?: return
        player?.release()
        val engine = ExoPlayer.Builder(this).build()
        player = engine
        video.player = engine
        engine.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                status.text = "Cannot play this channel. Check the provider, format or network."
            }
        })
        val item = MediaItem.Builder().setUri(Uri.parse(channel.url))
        if (channel.hls) item.setMimeType(MimeTypes.APPLICATION_M3U8)
        engine.setMediaItem(item.build())
        engine.seekTo(position)
        engine.prepare()
        engine.playWhenReady = true
        status.text = channel.name
    }

    override fun onStart() { super.onStart(); if (selected != null) play() }
    override fun onStop() {
        position = player?.currentPosition ?: position
        video.player = null
        player?.release()
        player = null
        super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("address", address.text.toString())
        selected?.let {
            out.putString("stream", it.url)
            out.putString("title", it.name)
            out.putBoolean("hls", it.hls)
            out.putLong("position", player?.currentPosition ?: position)
        }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() {
        generation++
        task?.cancel(true)
        executor.shutdownNow()
        player?.release()
        super.onDestroy()
    }
}
