package org.meetagain.app.core.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.Instant

/**
 * The last answer the server gave to one read, in one language. [body] is the answer's DTO as JSON, so a stored answer
 * goes through the same mapping as a fresh one.
 */
@Entity(tableName = "cached_answer", primaryKeys = ["key", "language"])
data class CachedAnswer(
    val key: String,
    val language: String,
    val body: String,
    @ColumnInfo(name = "synced_at") val syncedAt: Instant,
    @ColumnInfo(name = "used_at") val usedAt: Instant
)
