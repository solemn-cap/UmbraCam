package com.umbracam.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun enteringCameraWithoutGrantShowsExplanationAndCannotRecord() {
        assumeTrue(compose.activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        compose.onNodeWithText("Camera Studio").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_permission_reason)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_enable)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.camera_record)).performScrollTo().assertIsNotEnabled()
        // Reaching the explanatory screen without launching a permission contract is intentional.
    }
}
