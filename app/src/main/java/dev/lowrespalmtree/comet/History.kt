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
        fun get(uri: String): HistoryEntry?

        @Query("SELECT * FROM HistoryEntry ORDER BY lastVisit DESC")
        fun getAll(): List<HistoryEntry>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        fun insert(vararg entries: HistoryEntry)

        @Update
        fun update(vararg entries: HistoryEntry)
    }

    fun record(uri: String, title: String?) {
        val now = System.currentTimeMillis()
        val dao = Database.INSTANCE.historyEntryDao()
        val entry = dao.get(uri)
        if (entry == null)
            dao.insert(HistoryEntry(uri, title, now))
        else
            dao.update(entry.also { it.title = title; it.lastVisit = now })
    }
}