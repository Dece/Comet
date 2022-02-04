package dev.lowrespalmtree.comet

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.room.*
import java.lang.IllegalArgumentException
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.security.auth.x500.X500Principal

object Identities {
    @Entity
    data class Identity(
        /** ID. */
        @PrimaryKey(autoGenerate = true) val id: Int,
        /** Key to retrieve certificate from the keystore. */
        val key: String,
        /** Label for this identity. */
        var name: String?,
        /** URL paths configured to use this identity. */
        var urls: UrlList
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

        @Delete
        suspend fun delete(vararg identities: Identity)
    }

    suspend fun insert(key: String, name: String? = null): Long =
        Database.INSTANCE.identityDao().insert(Identity(0, key, name, arrayListOf()))

    suspend fun get(id: Long): Identity? =
        Database.INSTANCE.identityDao().get(id)

    suspend fun getAll(): List<Identity> =
        Database.INSTANCE.identityDao().getAll()

    suspend fun update(vararg identities: Identity) =
        Database.INSTANCE.identityDao().update(*identities)

    suspend fun delete(vararg identities: Identity) {
        for (identity in identities) {
            if (identity.key.isNotEmpty())
                deleteClientCert(identity.key)
        }
        Database.INSTANCE.identityDao().delete(*identities)
    }

    fun generateClientCert(alias: String, commonName: String) {
        val algo = KeyProperties.KEY_ALGORITHM_RSA
        val kpg = KeyPairGenerator.getInstance(algo, "AndroidKeyStore")
        val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        val spec = KeyGenParameterSpec.Builder(alias, purposes)
            .apply {
                if (commonName.isNotEmpty()) {
                    try {
                        setCertificateSubject(X500Principal("CN=$commonName"))
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "generateClientCert: bad common name: ${e.message}")
                    }
                }
            }
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        kpg.initialize(spec)
        kpg.generateKeyPair()
        Log.i(TAG, "generateClientCert: key pair with alias \"$alias\" has been generated")
    }

    private fun deleteClientCert(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            Log.i(TAG, "deleteClientCert: deleted entry with alias \"$alias\"")
        } else {
            Log.i(TAG, "deleteClientCert: no such alias \"$alias\"")
        }
    }

    private const val TAG = "Identities"
}