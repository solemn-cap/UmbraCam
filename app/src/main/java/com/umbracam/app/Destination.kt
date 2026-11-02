package com.umbracam.app

import androidx.annotation.StringRes

/** Stable route keys are saved by Navigation across activity recreation. */
enum class Destination(val route: String, @get:StringRes val title: Int, @get:StringRes val description: Int) {
    HOME("home", R.string.home, R.string.tagline),
    CAMERA("camera", R.string.camera_title, R.string.camera_description),
    REACTION("reaction", R.string.reaction_title, R.string.reaction_description),
    SOS("safety", R.string.sos_title, R.string.sos_description),
    VAULT("vault", R.string.vault_title, R.string.vault_description),
    SETTINGS("settings", R.string.settings, R.string.settings_details),
    CAMERA_SUPPORT("camera_support", R.string.camera_support_title, R.string.camera_support_intro);

    companion object {
        val studios = listOf(CAMERA, REACTION, SOS, VAULT)
    }
}
