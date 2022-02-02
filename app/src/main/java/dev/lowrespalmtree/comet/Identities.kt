package dev.lowrespalmtree.comet

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.room.*
import java.security.KeyPairGenerator

object Identities {
    @Entity
    data class Identity(
        /** ID. */
        @PrimaryKey(autoGenerate = true) val id: Int,
        /** Key to retrieve certificate from the keystore. */
        val key: String,
        /** Label for this identity. */
        var name: String?,
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
        suspend fun insert(identity: Identity): Long

        @Query("SELECT * FROM Identity WHERE :id = id")
        suspend fun get(id: Long): Identity?

        @Query("SELECT * FROM Identity ORDER BY id")
        suspend fun getAll(): List<Identity>

        @Update
        suspend fun update(vararg identities: Identity)

        @Query("SELECT * FROM IdentityUsage WHERE :identityId = identityId")
        suspend fun getUsagesFor(identityId: Int): List<IdentityUsage>
    }

    suspend fun insert(key: String, name: String? = null): Long =
        Database.INSTANCE.identityDao().insert(Identity(0, key, name))

    suspend fun get(id: Long): Identity? =
        Database.INSTANCE.identityDao().get(id)

    suspend fun getAll(): List<Identity> =
        Database.INSTANCE.identityDao().getAll()

    suspend fun update(vararg identities: Identity) =
        Database.INSTANCE.identityDao().update(*identities)

    fun generateClientCert(alias: String) {
        val algo = KeyProperties.KEY_ALGORITHM_RSA
        val kpg = KeyPairGenerator.getInstance(algo, "AndroidKeyStore")
        val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        val spec = KeyGenParameterSpec.Builder(alias, purposes)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        kpg.initialize(spec)
        kpg.generateKeyPair()
        Log.i(TAG, "generateClientCert: key pair with alias \"$alias\" has been generated")
    }

    private const val TAG = "Identities"
}