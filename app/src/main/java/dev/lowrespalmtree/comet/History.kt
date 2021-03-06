package dev.lowrespalmtree.comet

import androidx.room.*

object History {
    @Entity
    data class HistoryEntry(
        @PrimaryKey val uri: String,
        var title: String?,
        var lastVisit: Long,
    )

    @Dao
    interface HistoryEntryDao {
        @Query("SELECT * FROM HistoryEntry WHERE :uri = uri LIMIT 1")
        suspend fun get(uri: String): HistoryEntry?

        @Query("SELECT * FROM HistoryEntry ORDER BY lastVisit DESC")
        suspend fun getAll(): List<HistoryEntry>

        @Query("SELECT * FROM HistoryEntry ORDER BY lastVisit DESC LIMIT 1")
        suspend fun getLast(): HistoryEntry?

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insert(vararg entries: HistoryEntry)

        @Update
        suspend fun update(vararg entries: HistoryEntry)
    }

    suspend fun record(uri: String, title: String? = null) {
        val now = System.currentTimeMillis()
        val dao = Database.INSTANCE.historyEntryDao()
        val entry = dao.get(uri)
        if (entry == null)
            dao.insert(HistoryEntry(uri, title, now))
        else
            dao.update(entry.also { it.title = title; it.lastVisit = now })
    }

    suspend fun contains(uri: String): Boolean = get(uri) != null

    suspend fun get(uri: String): HistoryEntry? = Database.INSTANCE.historyEntryDao().get(uri)

    suspend fun getAll(): List<HistoryEntry> = Database.INSTANCE.historyEntryDao().getAll()

    suspend fun getLast(): HistoryEntry? = Database.INSTANCE.historyEntryDao().getLast()
}