package com.mohamed.hotspot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_DEVICE_TIME = "device_time"
    const val CHANNEL_BLOCKED = "blocked"

    const val MONITOR_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR, "مراقبة الهوت سبوت",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "إشعار مستمر أثناء مراقبة الأجهزة المتصلة" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEVICE_TIME, "وقت الأجهزة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "تنبيه لما وقت جهاز متصل يخلص" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BLOCKED, "أجهزة محظورة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "تنبيه لما جهاز محظور يتصل بنقطة الاتصال" }
        )
    }

    /** إشعار انتهاء وقت جهاز. النص المطلوب بالظبط: "وقت جهاز <label> خلص". */
    fun showTimeExpired(context: Context, label: String) {
        createChannels(context)
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DEVICE_TIME)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("انتهى وقت الجهاز")
            .setContentText("وقت جهاز $label خلص")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("وقت جهاز $label خلص. افصل النت عنه يدوياً: غيّر باسورد نقطة الاتصال أو اقفل نقطة الاتصال من الإعدادات."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        NotificationManagerCompat.from(context)
            .notify(("expired_$label").hashCode(), notification)
    }

    /** إشعار جهاز محظور اتصل: التطبيق ميقدرش يطرده، فبيطلب من المستخدم يتصرف. */
    fun showBlockedDevice(context: Context, label: String, mac: String) {
        createChannels(context)
        val openSettings = PendingIntent.getActivity(
            context, 0,
            Intent("android.settings.TETHER_SETTINGS"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKED)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("جهاز محظور متصل")
            .setContentText("الجهاز $label ($mac) محظور واتصل بنقطة الاتصال. غيّر باسورد نقطة الاتصال أو اقفلها.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("الجهاز $label ($mac) محظور واتصل بنقطة الاتصال. التطبيق ميقدرش يطرده لوحده، غيّر باسورد نقطة الاتصال أو اقفل نقطة الاتصال من الإعدادات."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openSettings)
            .addAction(0, "فتح إعدادات نقطة الاتصال", openSettings)
            .build()
        NotificationManagerCompat.from(context)
            .notify(("blocked_$mac").hashCode(), notification)
    }

    /** إشعار انتهاء مؤقت إيقاف نقطة الاتصال كلها. */
    fun showAutoOff(context: Context) {
        createChannels(context)
        val openSettings = PendingIntent.getActivity(
            context, 0,
            Intent("android.settings.TETHER_SETTINGS"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DEVICE_TIME)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("انتهى وقت نقطة الاتصال")
            .setContentText("وقت نقطة الاتصال خلص، اقفلها من الإعدادات.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openSettings)
            .addAction(0, "فتح إعدادات نقطة الاتصال", openSettings)
            .build()
        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    fun foregroundNotification(context: Context, connectedCount: Int): android.app.Notification {
        createChannels(context)
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("مدير الهوت سبوت شغال")
            .setContentText("بيراقب الأجهزة المتصلة — المتصلين دلوقتي: $connectedCount")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }
}
