package dev.lowrespalmtree.comet

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        History.HistoryEntry::class,
        Identities.Identity::class,
        Identities.IdentityUsage::class,
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyEntryDao(): History.HistoryEntryDao
    abstract fun identityDao(): Identities.IdentityDao
}

object Database {
    lateinit var INSTANCE: AppDatabase

    fun init(context: Context) {
        if (::INSTANCE.isInitialized)
            return
        INSTANCE = Room.databaseBuilder(context, AppDatabase::class.java, "comet.db").build()
    }
}