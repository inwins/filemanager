package com.example.filebrowser.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecretStore {
    private const val ALIAS = "file_browser_remote_password"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            "k:" + Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        }.getOrElse {
            "p:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        return runCatching {
            when {
                value.startsWith("k:") -> {
                    val bytes = Base64.decode(value.substring(2), Base64.NO_WRAP)
                    val iv = bytes.copyOfRange(0, 12)
                    val cipherText = bytes.copyOfRange(12, bytes.size)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(cipherText), Charsets.UTF_8)
                }
                value.startsWith("p:") -> String(
                    Base64.decode(value.substring(2), Base64.NO_WRAP),
                    Charsets.UTF_8,
                )
                else -> ""
            }
        }.getOrDefault("")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
