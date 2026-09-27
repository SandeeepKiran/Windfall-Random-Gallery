package com.mousy.windfall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mousy.windfall.data.media.MediaPermissions
import com.mousy.windfall.data.model.ThemeMode
import com.mousy.windfall.ui.GalleryApp
import com.mousy.windfall.ui.theme.WindfallTheme
import com.mousy.windfall.util.AndroidVersionGate
import com.mousy.windfall.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            viewModel.onPermissionsGranted()
        }
    }

    /** DEVICE-ONLY: Photo Picker for partial access on Android 13+. */
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) {
        viewModel.onPermissionsGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Until settings are read, the app believes no folders are chosen and would draw the
        // "choose folders" screen for a moment. The ViewModel caps this wait (READY_TIMEOUT_MS).
        installSplashScreen().setKeepOnScreenCondition { !viewModel.ready.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestMediaAccessIfNeeded()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val view = LocalView.current

            // Edge-to-edge draws under the bars, so bar icons have to follow the app theme
            // instead of the system one or they vanish against the header.
            LaunchedEffect(settings.themeMode, view) {
                val controller = WindowCompat.getInsetsController(window, view)
                val light = settings.themeMode == ThemeMode.LIGHT
                controller.isAppearanceLightStatusBars = light
                controller.isAppearanceLightNavigationBars = light
            }

            WindfallTheme(
                themeMode = settings.themeMode,
                amoled = settings.amoled,
                accent = settings.accent,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    GalleryApp(
                        viewModel = viewModel,
                        onRequestOrientation = { orientation ->
                            // DEVICE-ONLY: Multi-Video landscape lock, or sensor for viewer video
                            requestedOrientation = orientation
                        },
                    )
                }
            }
        }
    }

    private fun requestMediaAccessIfNeeded() {
        if (MediaPermissions.hasReadAccess(this)) {
            viewModel.onPermissionsGranted()
            return
        }

        if (AndroidVersionGate.isAndroid16Plus && MediaPermissions.shouldOfferPhotoPicker(this)) {
            photoPickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                ),
            )
            return
        }

        permissionLauncher.launch(MediaPermissions.permissionsToRequest())
    }
}
