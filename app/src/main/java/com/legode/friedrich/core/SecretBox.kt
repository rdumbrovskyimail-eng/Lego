package com.legode.friedrich.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование API-ключа ключом из Android Keystore (AES-256-GCM).
 * Ключ шифрования не покидает защищённое хранилище устройства, в настройках лежит только шифртекст.
 * Если Keystore на устройстве сбоит, значение хранится с пометкой "p:" (в приватной памяти приложения).
 */
object SecretBox {

    private const val ALIAS = "friedrich_master_key_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PREFIX_ENC = "e:"
    private const val PREFIX_PLAIN = "p:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER)
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(1 + iv.size + body.size)
            packed[0] = iv.size.toByte()
            System.arraycopy(iv, 0, packed, 1, iv.size)
            System.arraycopy(body, 0, packed, 1 + iv.size, body.size)
            PREFIX_ENC + Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (_: Throwable) {
            PREFIX_PLAIN + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun open(sealed: String): String {
        if (sealed.isEmpty()) return ""
        return try {
            when {
                sealed.startsWith(PREFIX_PLAIN) ->
                    String(Base64.decode(sealed.substring(PREFIX_PLAIN.length), Base64.NO_WRAP), Charsets.UTF_8)

                sealed.startsWith(PREFIX_ENC) -> {
                    val packed = Base64.decode(sealed.substring(PREFIX_ENC.length), Base64.NO_WRAP)
                    val ivLen = packed[0].toInt()
                    val iv = packed.copyOfRange(1, 1 + ivLen)
                    val body = packed.copyOfRange(1 + ivLen, packed.size)
                    val cipher = Cipher.getInstance(TRANSFORM)
                    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(body), Charsets.UTF_8)
                }

                else -> ""
            }
        } catch (_: Throwable) {
            ""
        }
    }
}
