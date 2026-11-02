package com.brandx.app.data

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object PlaylistParser {
    fun valid(url: String): Boolean = try {
        val u = URI(url)
        (u.scheme.equals("http", true) || u.scheme.equals("https", true)) && !u.host.isNullOrBlank() && u.userInfo == null
    } catch (_: Exception) { false }

    fun download(url: String): String {
        var next = url
        val deadline = System.nanoTime() + 45_000_000_000L
        repeat(6) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            require(valid(next)) { "Некорректная ссылка" }
            val connection = URL(next).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "VEDA-IPTV/2.0")
            try {
                if (System.nanoTime() > deadline) error("Превышено время загрузки")
                when (connection.responseCode) {
                    in listOf(301, 302, 303, 307, 308) -> {
                        next = URI(next).resolve(connection.getHeaderField("Location") ?: error("Нет адреса перенаправления")).toString()
                    }
                    in 200..299 -> {
                        val output = java.io.ByteArrayOutputStream()
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                                if (System.nanoTime() > deadline) error("Превышено время загрузки")
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 5 * 1024 * 1024) error("Плейлист больше 5 МБ")
                                output.write(buffer, 0, count)
                            }
                        }
                        val text = output.toString("UTF-8").removePrefix("\uFEFF")
                        require(text.trimStart().startsWith("#EXTM3U")) { "Ожидается плейлист M3U/M3U8" }
                        return text
                    }
                    else -> error("Ошибка сервера HTTP ${connection.responseCode}")
                }
            } finally { connection.disconnect() }
        }
        error("Слишком много перенаправлений")
    }

    private fun attr(line: String, key: String): String {
        return Regex("(?:^|\\s)" + Regex.escape(key) + "\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.get(1).orEmpty()
    }

    private fun title(line: String): String {
        var quoted = false
        line.forEachIndexed { index, char ->
            if (char == '"') quoted = !quoted
            if (char == ',' && !quoted) return line.substring(index + 1).trim()
        }
        return ""
    }

    fun parse(text: String, playlistId: Long): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = ""
        var group = ""
        var logo = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:", true) -> {
                    name = title(line).ifBlank { attr(line, "tvg-name") }
                    group = attr(line, "group-title")
                    logo = attr(line, "tvg-logo")
                }
                line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim()
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val url = try { URI("http://placeholder.local/").resolve(line).toString() } catch (_: Exception) { "" }
                    if (valid(url)) {
                        result.add(
                            Channel(
                                playlistId = playlistId,
                                name = name.ifBlank { "Канал ${result.size + 1}" },
                                url = url,
                                category = group.ifBlank { "Без категории" },
                                logo = logo,
                                hls = url.contains(".m3u8", true)
                            )
                        )
                    }
                    name = ""; group = ""; logo = ""
                    if (result.size >= 20000) break
                }
            }
        }
        require(result.isNotEmpty()) { "Каналы HTTP/HTTPS не найдены" }
        return result.distinctBy { it.url }
    }
}
