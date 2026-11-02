package com.umbracam.app.vault

import com.umbracam.app.camera.AndroidVideoInspector
import com.umbracam.app.camera.RecordingFileStore
import com.umbracam.app.camera.VaultMigrationReport
import java.io.File

data class VaultMigrationStatus(val camera: VaultMigrationReport, val screen: VaultMigrationReport) {
    val failed: Int get() = camera.failed + screen.failed
    val migrated: Int get() = camera.migrated + screen.migrated
}

/** Serializes camera/screen migration and keeps old files until authenticated round-trip verification. */
class VaultMigrationCoordinator(
    private val cameraRoot: File,
    private val screenRoot: File,
    private val codec: EncryptedFileCodec,
) {
    @Volatile private var lastSuccessful: VaultMigrationStatus? = null

    @Synchronized fun ensureMigrated(): VaultMigrationStatus {
        lastSuccessful?.let { return it }
        val inspector = AndroidVideoInspector()
        val camera = RecordingFileStore(cameraRoot, inspector, vaultCodec = codec).migratePlaintextRecordings()
        val screen = RecordingFileStore(screenRoot, inspector, vaultCodec = codec).migratePlaintextRecordings()
        val result = VaultMigrationStatus(camera, screen)
        if (result.failed == 0) lastSuccessful = result
        return result
    }
}
