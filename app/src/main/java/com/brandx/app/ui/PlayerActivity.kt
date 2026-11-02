package com.brandx.app.ui

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var video: PlayerView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        video = PlayerView(this)
        setContentView(video)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onStart() {
        super.onStart()
        val url = intent.getStringExtra("url") ?: return
        player?.release()
        val engine = ExoPlayer.Builder(this).build()
        player = engine
        video.player = engine
        engine.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Канал недоступен или формат не поддерживается", Toast.LENGTH_LONG).show()
            }
        })
        val item = MediaItem.Builder().setUri(url)
        if (intent.getBooleanExtra("hls", false)) item.setMimeType(MimeTypes.APPLICATION_M3U8)
        engine.setMediaItem(item.build())
        engine.prepare()
        engine.playWhenReady = true
    }

    override fun onStop() {
        video.player = null
        player?.release()
        player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }
}
