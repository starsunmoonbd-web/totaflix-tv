package com.totaflixtv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.graphics.Color
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var listPanel: LinearLayout
    private lateinit var listScroll: ScrollView
    private val channels = mutableListOf<Channel>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var selected = 0
    private var listOpen = false
    private var numberBuffer = ""

    data class Channel(val name: String, val url: String, val logo: String = "")

    private val playlistUrl =
        "https://raw.githubusercontent.com/starsunmoonbd-web/totaflix-tv/main/playlist.m3u"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        player = ExoPlayer.Builder(this).build()
        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = false
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        listPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.rgb(18, 22, 28))
            visibility = View.GONE
        }
        val title = TextView(this).apply {
            text = "TotaFlix TV  •  Channels"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(8, 8, 8, 18)
        }
        listPanel.addView(title)
        listScroll = ScrollView(this)
        listPanel.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val panelParams = FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.48f).toInt(),
            -1,
            Gravity.START
        )
        root.addView(listPanel, panelParams)
        setContentView(root)

        loadPlaylist()
    }

    private fun loadPlaylist() {
        thread {
            try {
                val text = URL(playlistUrl).openConnection().run {
                    connectTimeout = 12000
                    readTimeout = 15000
                    getInputStream().bufferedReader().use { it.readText() }
                }
                val parsed = parseM3u(text)
                mainHandler.post {
                    channels.clear()
                    channels.addAll(parsed)
                    if (channels.isNotEmpty()) {
                        selected = 0
                        playChannel(selected)
                        buildChannelList()
                    } else {
                        showMessage("No channels in playlist")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    showMessage("Playlist error. Check internet or M3U file.")
                }
            }
        }
    }

    private fun parseM3u(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = ""
        var logo = ""
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',').trim().ifEmpty { "Channel ${result.size + 1}" }
                logo = Regex("""tvg-logo=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.get(1).orEmpty()
            } else if (line.isNotEmpty() && !line.startsWith("#") &&
                (line.startsWith("http://") || line.startsWith("https://"))) {
                result.add(Channel(name.ifEmpty { "Channel ${result.size + 1}" }, line, logo))
                name = ""
                logo = ""
            }
        }
        return result
    }

    private fun buildChannelList() {
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        channels.forEachIndexed { index, channel ->
            val row = TextView(this).apply {
                text = "%03d   %s".format(index + 1, channel.name)
                textSize = 19f
                setTextColor(if (index == selected) Color.BLACK else Color.WHITE)
                setBackgroundColor(if (index == selected) Color.rgb(0, 214, 160) else Color.TRANSPARENT)
                setPadding(16, 15, 12, 15)
                isFocusable = true
                setOnClickListener {
                    selected = index
                    playChannel(index)
                    buildChannelList()
                }
            }
            rows.addView(row)
        }
        listScroll.removeAllViews()
        listScroll.addView(rows)
        listScroll.post {
            val rowHeight = 60
            listScroll.scrollTo(0, (selected * rowHeight).coerceAtMost(
                (channels.size * rowHeight - listScroll.height).coerceAtLeast(0)
            ))
        }
    }

    private fun playChannel(index: Int) {
        if (index !in channels.indices) return
        selected = index
        player.setMediaItem(MediaItem.fromUri(channels[index].url))
        player.prepare()
        player.playWhenReady = true
        if (listOpen) buildChannelList()
    }

    private fun toggleList() {
        if (channels.isEmpty()) return
        listOpen = !listOpen
        listPanel.visibility = if (listOpen) View.VISIBLE else View.GONE
        if (listOpen) buildChannelList()
    }

    private fun showMessage(message: String) {
        val box = TextView(this).apply {
            text = message
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        root.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        mainHandler.postDelayed({ root.removeView(box) }, 5000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!listOpen) toggleList()
                else {
                    playChannel(selected)
                    toggleList()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (listOpen && channels.isNotEmpty()) {
                    selected = (selected - 1).coerceAtLeast(0)
                    buildChannelList()
                } else playChannel((selected - 1).coerceAtLeast(0))
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (listOpen && channels.isNotEmpty()) {
                    selected = (selected + 1).coerceAtMost(channels.lastIndex)
                    buildChannelList()
                } else playChannel((selected + 1).coerceAtMost(channels.lastIndex))
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                playChannel((selected + 1).coerceAtMost(channels.lastIndex))
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                playChannel((selected - 1).coerceAtLeast(0))
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (listOpen) {
                    toggleList()
                    return true
                }
            }
        }
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val digit = keyCode - KeyEvent.KEYCODE_0
            numberBuffer += digit.toString()
            mainHandler.removeCallbacksAndMessages("digits")
            mainHandler.postAtTime({
                val number = numberBuffer.toIntOrNull()
                numberBuffer = ""
                if (number != null && number in 1..channels.size) {
                    playChannel(number - 1)
                    if (listOpen) toggleList()
                }
            }, "digits", android.os.SystemClock.uptimeMillis() + 1000)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
