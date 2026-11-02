package com.brandx.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val updatedAt: Long = 0,
    val channelCount: Int = 0
)

@Entity(tableName = "channels", primaryKeys = ["playlistId", "url"])
data class Channel(
    val playlistId: Long,
    val name: String,
    val url: String,
    val category: String,
    val logo: String = "",
    val hls: Boolean = false
)

@Entity(tableName = "favorites", primaryKeys = ["playlistId", "url"])
data class Favorite(
    val playlistId: Long,
    val url: String,
    val addedAt: Long
)

@Entity(tableName = "recent", primaryKeys = ["playlistId", "url"])
data class RecentChannel(
    val playlistId: Long,
    val url: String,
    val watchedAt: Long
)
