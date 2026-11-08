package com.nexo.escritorio.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class BatteryInfo(val level: Int, val charging: Boolean)

enum class NetKind { NONE, WIFI, CELL, ETHERNET }

/** Hora actual, actualizada cada segundo. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(initialValue = LocalDateTime.now()) {
    while (true) {
        delay(1000L - System.currentTimeMillis() % 1000L)
        value = LocalDateTime.now()
    }
}

@Composable
fun rememberBattery(): BatteryInfo {
    val context = LocalContext.current
    var info by remember { mutableStateOf(BatteryInfo(-1, false)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                info = BatteryInfo(
                    if (level >= 0) level * 100 / scale else -1,
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return info
}

@Composable
fun rememberNetwork(): NetKind {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(NetKind.NONE) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                kind = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetKind.ETHERNET
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELL
                    else -> NetKind.NONE
                }
            }

            override fun onLost(network: Network) {
                kind = NetKind.NONE
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return kind
}

fun formatTime(t: LocalDateTime, h24: Boolean): String =
    t.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", Locale.getDefault()))

fun formatDateLong(t: LocalDateTime): String =
    t.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun formatDateShort(t: LocalDateTime): String =
    t.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()))

fun greeting(t: LocalDateTime): String = when (t.hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}
