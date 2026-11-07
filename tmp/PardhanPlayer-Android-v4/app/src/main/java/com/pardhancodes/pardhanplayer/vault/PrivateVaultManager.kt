package com.pardhancodes.pardhanplayer.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

class PrivateVaultManager(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    fun getVaultDir(): File = File(context.filesDir, "private_vault").apply { mkdirs() }
    private fun tmpDir(): File = File(context.cacheDir, "vault_tmp").apply { mkdirs() }

    fun listVaultFiles(): List<File> =
        getVaultDir().listFiles { f -> f.name.endsWith(".pvault") }?.sortedBy { it.name } ?: emptyList()

    private fun encryptedFor(dest: File) = EncryptedFile.Builder(
        context, dest, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    fun encryptFileToVault(source: File, targetName: String): File {
        val dest = File(getVaultDir(), "$targetName.pvault")
        if (dest.exists()) dest.delete() // EncryptedFile refuses to overwrite
        source.inputStream().use { input ->
            encryptedFor(dest).openFileOutput().use { out -> input.copyTo(out) }
        }
        return dest
    }

    /** Copies a picked video/audio (content Uri) straight into the encrypted vault. */
    fun importUri(uri: Uri): File {
        var name = "media_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i)
        }
        val dest = File(getVaultDir(), "$name.pvault")
        if (dest.exists()) dest.delete()
        context.contentResolver.openInputStream(uri)!!.use { input ->
            encryptedFor(dest).openFileOutput().use { out -> input.copyTo(out) }
        }
        return dest
    }

    /** Decrypts to a temporary cache file so ExoPlayer can seek. Cleared by clearTemp(). */
    fun decryptToCache(vaultFile: File): File {
        val out = File(tmpDir(), vaultFile.name.removeSuffix(".pvault"))
        if (out.exists()) out.delete()
        encryptedFor(vaultFile).openFileInput().use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return out
    }

    fun clearTemp() {
        tmpDir().listFiles()?.forEach { it.delete() }
    }

    fun authenticateBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Pardhan Private Vault")
            .setSubtitle("Verify identity to access encrypted local media")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}
