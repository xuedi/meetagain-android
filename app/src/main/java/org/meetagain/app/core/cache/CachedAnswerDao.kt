package org.meetagain.app.core.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedAnswerDao {
    @Query("SELECT * FROM cached_answer WHERE `key` = :key AND language = :language")
    fun observe(key: String, language: String): Flow<CachedAnswer?>

    @Upsert
    suspend fun put(answer: CachedAnswer)

    @Query("DELETE FROM cached_answer WHERE `key` = :key AND language = :language")
    suspend fun delete(key: String, language: String)

    @Query("UPDATE cached_answer SET used_at = :now WHERE `key` = :key AND language = :language")
    suspend fun markUsed(key: String, language: String, now: Instant)

    @Query("DELETE FROM cached_answer WHERE used_at < :before")
    suspend fun deleteUnusedSince(before: Instant)
}
