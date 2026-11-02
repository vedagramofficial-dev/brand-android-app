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

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): Playlist?

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

    @Transaction
    suspend fun replace(id: Long, playlist: Playlist, channels: List<Channel>) {
        val saved = save(playlist.copy(id = id))
        clearChannels(saved)
        channels.chunked(500).forEach { insertChannels(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY name COLLATE NOCASE")
    fun observeChannels(playlistId: Long): Flow<List<Channel>>

    @Query("SELECT DISTINCT category FROM channels WHERE playlistId = :playlistId ORDER BY category COLLATE NOCASE")
    fun observeCategories(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE LIMIT 500")
    suspend fun search(playlistId: Long, query: String): List<Channel>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND url = :url LIMIT 1")
    suspend fun get(playlistId: Long, url: String): Channel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND url = :url")
    suspend fun removeFavorite(playlistId: Long, url: String)

    @Query("SELECT * FROM favorites WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observeFavorites(playlistId: Long): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRecent(channel: RecentChannel)

    @Query("SELECT * FROM recent WHERE playlistId = :playlistId ORDER BY watchedAt DESC LIMIT 50")
    fun observeRecent(playlistId: Long): Flow<List<RecentChannel>>
}
