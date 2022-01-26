package dev.lowrespalmtree.comet

import androidx.room.*

object Identities {
    @Entity
    data class Identity(
        /** ID. */
        @PrimaryKey(autoGenerate = true) val id: Int,
        /** Key to retrieve certificate from the keystore. */
        val key: String,
        /** Label for this identity. */
        val name: String?,
    )

    @Entity
    data class IdentityUsage(
        /** ID. */
        @PrimaryKey(autoGenerate = true) val id: Int,
        /** URL path where an identity can be used. */
        val uri: String,
        /** ID of the Identity to use. */
        val identityId: Int
    )

    @Dao
    interface IdentityDao {
        @Insert
        suspend fun insert(vararg entries: Identity)

        @Query("SELECT * FROM IdentityUsage WHERE :identityId = identityId")
        fun getUsagesFor(identityId: Int): List<IdentityUsage>
    }

    suspend fun insert(key: String, name: String? = null) {
        Database.INSTANCE.identityDao().insert(Identity(0, key, name))
    }
}