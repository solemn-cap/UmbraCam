package com.umbracam.core.recording

import kotlinx.coroutines.flow.StateFlow

enum class RecordingState { IDLE, PREPARING, RECORDING, INTERRUPTED, RECOVERING, STOPPING, SAVED, FAILED }
enum class RecordingKind { STANDARD, SOS }
enum class CaptureSource { FRONT_CAMERA, REAR_CAMERA, SCREEN, MICROPHONE, INTERNAL_AUDIO }
enum class RecordingErrorCode { PERMISSION_DENIED, SOURCE_UNAVAILABLE, CONSENT_REVOKED, STORAGE_FULL, ENCODER_FAILED, FINALIZATION_FAILED, PREVIOUS_SESSION_INTERRUPTED, PERSISTENCE_FAILED, THERMAL_LIMIT, UNKNOWN }

data class RecordingError(val code: RecordingErrorCode, val recoverable: Boolean)

/** Sources are requirements, not a claim that the device supports the combination. */
data class RecordingRequest(val requiredSources: Set<CaptureSource>) {
    init { require(requiredSources.isNotEmpty()) { "At least one capture source is required" } }
}

/** Only finalized segments belong here. A partial file must never be published as saved. */
data class SavedSegment(val storageId: String, val durationMillis: Long) {
    init {
        require(storageId.isNotBlank())
        require(durationMillis >= 0)
    }
}

data class RecordingSession(
    val id: String,
    val request: RecordingRequest,
    val state: RecordingState,
    val availableSources: Set<CaptureSource> = emptySet(),
    val completedSegments: List<SavedSegment> = emptyList(),
    val error: RecordingError? = null,
    val attemptId: String = id,
    val kind: RecordingKind = RecordingKind.STANDARD,
) {
    init {
        require(id.isNotBlank())
        require(attemptId.isNotBlank())
        require(kind != RecordingKind.SOS || request.requiredSources == setOf(CaptureSource.REAR_CAMERA)) {
            "SOS recordings require only the rear camera"
        }
        require(state != RecordingState.RECORDING || availableSources.containsAll(request.requiredSources)) {
            "Recording requires every requested source to be available"
        }
        require(state != RecordingState.SAVED || completedSegments.isNotEmpty())
        require(state != RecordingState.FAILED || error != null)
    }
}

/** Future implementation owns authorization, serialized transitions and finalization.
 * No implementation or service exists in Phase 0. Starting never implies permission.
 * Platform callbacks must publish INTERRUPTED before recovery if a source is lost.
 */
interface RecordingEngine {
    val session: StateFlow<RecordingSession?>
    suspend fun start(request: RecordingRequest)
    suspend fun stop()
    /** Must revalidate permissions, source access and user authorization. */
    suspend fun recover()
}
