package dev.lowrespalmtree.comet

import android.content.Context
import android.util.Base64
import androidx.room.*
import androidx.room.Database

@Database(
    entities = [
        History.HistoryEntry::class,
        Identities.Identity::class,
    ],
    version = 1
)
@TypeConverters(Converters::class)
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

typealias UrlList = ArrayList<String>

class Converters {
    @TypeConverter
    fun fromUrlList(value: UrlList?): String? =
        value?.joinToString("-") {
            Base64.encodeToString(it.encodeToByteArray(), Base64.DEFAULT)
        }

    @TypeConverter
    fun stringToUrlList(value: String?): UrlList? =
        value?.split("-")?.map { Base64.decode(it, Base64.DEFAULT).decodeToString() } as UrlList?
}