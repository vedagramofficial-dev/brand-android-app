package com.brandx.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Playlist::class, Channel::class, Favorite::class, RecentChannel::class],
    version = 1,
    exportSchema = false
)
abstract class VedaDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile private var instance: VedaDatabase? = null

        fun get(context: Context): VedaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, VedaDatabase::class.java, "veda_iptv.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
