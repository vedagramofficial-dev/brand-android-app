package com.brandx.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM channels WHERE playlistId = :id")
    suspend fun clearChannels(id: Long)

    @Query("DELETE FROM favorites WHERE playlistId = :id")
    suspend fun clearFavorites(id: Long)

    @Query("DELETE FROM recent WHERE playlistId = :id")
    suspend fun clearRecent(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Transaction
    suspend fun replace(id: Long, playlist: Playlist, channels: List<Channel>) {
        val savedId = save(playlist.copy(id = id))
        clearChannels(savedId)
        channels.chunked(500).forEach { insertChannels(it) }
    }
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY name COLLATE NOCASE")
    fun observeChannels(playlistId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM favorites WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observeFavorites(playlistId: Long): Flow<List<Favorite>>

    @Query("SELECT * FROM recent WHERE playlistId = :playlistId ORDER BY watchedAt DESC LIMIT 50")
    fun observeRecent(playlistId: Long): Flow<List<RecentChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND url = :url")
    suspend fun removeFavorite(playlistId: Long, url: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRecent(channel: RecentChannel)
}
