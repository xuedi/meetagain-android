package org.meetagain.app.core.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant

/** Public content kept for reading offline. Disposable: a schema change drops it instead of migrating. */
@Database(entities = [CachedAnswer::class], version = 1, exportSchema = false)
@TypeConverters(InstantConverter::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun answers(): CachedAnswerDao

    companion object {
        fun open(context: Context): CacheDatabase = Room.databaseBuilder(context, CacheDatabase::class.java, "cache.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}

class InstantConverter {
    @TypeConverter
    fun toMillis(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)
}
