package com.umbracam.app

import android.app.Application
import com.umbracam.app.camera.CameraRecordingController
import java.io.File

class UmbraCamApplication : Application() {
    val vaultCodec by lazy { com.umbracam.app.vault.EncryptedFileCodec(com.umbracam.app.vault.AndroidVaultKeySource()) }
    val vaultMigration by lazy { com.umbracam.app.vault.VaultMigrationCoordinator(
        File(noBackupFilesDir, "camera"), File(noBackupFilesDir, "screen"), vaultCodec) }
    val vaultMetadata by lazy { com.umbracam.app.vault.VaultMetadataStore(
        File(noBackupFilesDir, "vault/metadata.enc"), File(cacheDir, "vault-transient"), vaultCodec) }
    val vaultAccess by lazy { com.umbracam.app.vault.VaultAccessManager(this) }
    val captureThermals by lazy { CaptureThermals(this) }
    var libraryOperation: Boolean = false
        internal set
    val recording: CameraRecordingController by lazy { CameraRecordingController(this) }
    val screenRecording: com.umbracam.app.screen.ScreenRecordingController by lazy { com.umbracam.app.screen.ScreenRecordingController(this) }

    override fun onCreate() {
        super.onCreate()
        File(cacheDir, "vault-transient").deleteRecursively()
    }
}
