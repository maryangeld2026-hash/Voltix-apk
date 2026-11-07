package com.voltix.guardian.engine

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

data class PermState(
    val notifications: Boolean = false,
    val writeSettings: Boolean = false,
    val usage: Boolean = false,
    val secure: Boolean = false,
    val batteryOpt: Boolean = false
)

object Perms {

    fun read(ctx: Context): PermState = PermState(
        notifications = notifications(ctx),
        writeSettings = Settings.System.canWrite(ctx),
        usage = usage(ctx),
        secure = ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED,
        batteryOpt = try {
            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (_: Exception) {
            false
        }
    )

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun usage(ctx: Context): Boolean = try {
        val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    fun adbCommand(ctx: Context): String =
        "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
}

/** Atajos a las pantallas del sistema y de HyperOS. Cada uno prueba varias rutas y cae a ajustes genéricos. */
object Launch {

    private fun tryStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun details(ctx: Context, pkg: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    fun writeSettings(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun usageAccess(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun ignoreBatteryOpt(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )

    fun xiaomiAutostart(ctx: Context) = tryStart(
        ctx,
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        ),
        details(ctx, ctx.packageName)
    )

    fun appBattery(ctx: Context, pkg: String, label: String) = tryStart(
        ctx,
        Intent().setComponent(
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        ).putExtra("package_name", pkg).putExtra("package_label", label),
        details(ctx, pkg)
    )

    fun batterySettings(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun display(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun network(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun developerOptions(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
}
