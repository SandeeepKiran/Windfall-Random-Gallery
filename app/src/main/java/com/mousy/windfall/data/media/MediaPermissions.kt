package com.mousy.windfall.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mousy.windfall.util.AndroidVersionGate

object MediaPermissions {

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * True when the app can read media via MediaStore.
     *
     * Android 14–16: full `READ_MEDIA_*` **or** partial `READ_MEDIA_VISUAL_USER_SELECTED`
     * both count as usable access (they are mutually exclusive in practice).
     * SAF tree URIs still work without these permissions.
     */
    fun hasReadAccess(context: Context): Boolean = when {
        AndroidVersionGate.isAndroid13Plus -> {
            val fullImages = granted(context, Manifest.permission.READ_MEDIA_IMAGES)
            val fullVideo = granted(context, Manifest.permission.READ_MEDIA_VIDEO)
            val partial = AndroidVersionGate.supportsPartialMediaAccess &&
                granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            (fullImages && fullVideo) || partial
        }
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPartialVisualAccess(context: Context): Boolean {
        if (!AndroidVersionGate.supportsVisualUserSelected) return false
        return granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    fun hasFullMediaAccess(context: Context): Boolean {
        if (!AndroidVersionGate.isAndroid13Plus) {
            return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return granted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
            granted(context, Manifest.permission.READ_MEDIA_VIDEO)
    }

    /**
     * Permissions to request based on API level.
     * DEVICE-ONLY: real runtime prompts.
     *
     * Android 16+: request granular media + visual user-selected so the system can
     * offer full or limited access dialogs (privacy-first path).
     * Android 13–15: classic granular media permissions.
     * Android 11–12: legacy storage read.
     */
    fun permissionsToRequest(): Array<String> = when {
        AndroidVersionGate.isAndroid16Plus -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        AndroidVersionGate.isAndroid14Plus -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        AndroidVersionGate.isAndroid13Plus -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Prefer Photo Picker on API 33+ when user grants limited access. */
    fun shouldOfferPhotoPicker(context: Context): Boolean =
        AndroidVersionGate.supportsPhotoPicker &&
            (!hasFullMediaAccess(context) || hasPartialVisualAccess(context))

    fun maxSdkForLegacyStorage(): Int? =
        if (Build.VERSION.SDK_INT >= 33) null else Build.VERSION_CODES.S_V2
}
