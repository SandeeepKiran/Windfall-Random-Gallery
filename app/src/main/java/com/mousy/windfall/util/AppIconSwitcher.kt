package com.mousy.windfall.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Launcher icons the user can pick in Settings. Each maps to a manifest activity-alias;
 * the enum lives here (not in the manifest strings) so Settings can render labels and
 * previews without touching PackageManager.
 */
enum class AppLauncherIcon(val aliasSuffix: String, val label: String) {
    WINDROSE("IconWindrose", "Windrose"),
    CLASSIC("IconClassic", "Classic art"),
    MONO("IconMono", "Mono"),
}

/**
 * Runtime launcher-icon switching via activity-alias enable state. The enabled state is
 * persisted by the system across reboots, so it is the single source of truth — no DataStore.
 */
object AppIconSwitcher {

    /** Aliases are declared relative to the manifest namespace, not applicationId. */
    private const val ALIAS_PACKAGE = "com.mousy.windfall"

    private fun component(context: Context, icon: AppLauncherIcon) =
        ComponentName(context.packageName, "$ALIAS_PACKAGE.${icon.aliasSuffix}")

    /**
     * The currently active icon. A freshly installed app has every alias in DEFAULT state
     * (only Windrose is manifest-enabled), so "none explicitly enabled" means the default.
     */
    fun current(context: Context): AppLauncherIcon {
        val pm = context.packageManager
        return AppLauncherIcon.entries.firstOrNull { icon ->
            pm.getComponentEnabledSetting(component(context, icon)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppLauncherIcon.WINDROSE
    }

    /** Enables the chosen alias first, then disables the rest — the launcher entry never vanishes. */
    fun set(context: Context, icon: AppLauncherIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            component(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AppLauncherIcon.entries.filter { it != icon }.forEach { other ->
            pm.setComponentEnabledSetting(
                component(context, other),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
