package com.windwidget

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePrefs {
    internal fun fallbackName(name: String) = "${name}_unencrypted"

    fun get(context: Context, name: String): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            EncryptedSharedPreferences.create(
                name,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore isn't available on the device. Use a separate file: writing plain text into
            // the encrypted one would mix the two and leave readable keys next to encrypted ones.
            context.getSharedPreferences(fallbackName(name), Context.MODE_PRIVATE)
        }
    }
}
