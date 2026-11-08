package com.nexo.escritorio

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference

data class NotifItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean,
)

/** Lee las notificaciones (solo si el usuario lo permite) para mostrarlas en el escritorio. */
class NotificationService : NotificationListenerService() {

    companion object {
        val items = MutableStateFlow<List<NotifItem>>(emptyList())
        val connected = MutableStateFlow(false)
        private var instance: WeakReference<NotificationService>? = null

        fun isEnabled(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun dismiss(key: String) {
            instance?.get()?.let { runCatching { it.cancelNotification(key) } }
        }

        fun dismissAll() {
            instance?.get()?.let { runCatching { it.cancelAllNotifications() } }
        }
    }

    override fun onListenerConnected() {
        instance = WeakReference(this)
        connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        connected.value = false
        instance = null
        items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        items.value = active
            .filter { sbn ->
                val flags = sbn.notification.flags
                sbn.packageName != packageName &&
                    !sbn.isOngoing &&
                    (flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            .sortedByDescending { it.postTime }
            .take(50)
            .map { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                NotifItem(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postTime = sbn.postTime,
                    intent = sbn.notification.contentIntent,
                    clearable = sbn.isClearable,
                    autoCancel = (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
                )
            }
    }
}
