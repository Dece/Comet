package dev.lowrespalmtree.comet

import androidx.room.*

object History {
    @Entity
    data class HistoryEntry(
        @PrimaryKey @ColumnInfo(name = "uri") val uri: String,
        @ColumnInfo(name = "title") var title: String?,
        @ColumnInfo(name = "lastVisit") var lastVisit: Long,
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

    suspend fun record(uri: String, title: String?) {
        val now = System.currentTimeMillis()
        val dao = Database.INSTANCE.historyEntryDao()
        val entry = dao.get(uri)
        if (entry == null)
            dao.insert(HistoryEntry(uri, title, now))
        else
            dao.update(entry.also { it.title = title; it.lastVisit = now })
    }

    suspend fun getAll(): List<HistoryEntry> = Database.INSTANCE.historyEntryDao().getAll()

    suspend fun getLast(): HistoryEntry? = Database.INSTANCE.historyEntryDao().getLast()
}