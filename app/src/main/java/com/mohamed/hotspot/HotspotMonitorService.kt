package com.mohamed.hotspot

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * خدمة مراقبة في المقدمة: بتتابع الأجهزة المتصلة بنقطة اتصال النظام،
 * وبتحسب الوقت لكل جهاز، وبتنبّه لما وقت جهاز يخلص أو جهاز محظور يتصل،
 * وبتنبّه لما مؤقت إيقاف نقطة الاتصال يخلص.
 *
 * كشف الأجهزة بيتم بقراءة جدول ARP من /proc/net/arp فقط.
 * (واجهات WifiManager.SoftApCallback وWifiClient وSoftApCapability
 *  مش موجودة في الـ SDK العام — دي واجهات نظام مخفية، فالكود اللي
 *  كان بيستخدمها مبيتبنيش، واتشالت.)
 *
 * حدود أندرويد الصريحة (مش قصور في التطبيق):
 * - التطبيق ميقدرش يشغّل نقطة اتصال إنترنت بنفسه، ولا يطرد جهاز متصل،
 *   ولا يفرض قايمة حظر. دي صلاحيات تطبيق الإعدادات بتاع النظام بس.
 * - اللي بيعمله التطبيق: مراقبة + تنبيه، والمستخدم هو اللي بيفصل يدوياً.
 */
class HotspotMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkAll()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationHelper.MONITOR_NOTIFICATION_ID,
            NotificationHelper.foregroundNotification(this, 0)
        )
        HotspotPrefs.setMonitoring(this, true)
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        checkAll()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        HotspotPrefs.setMonitoring(this, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** الأجهزة المتصلة دلوقتي: من جدول ARP. */
    private fun currentMacs(): Map<String, String> {
        return ArpReader.readMacToIp()
    }

    private fun checkAll() {
        val now = System.currentTimeMillis()
        val connected = currentMacs()
        HotspotPrefs.setConnectedMacs(this, connected.keys)
        HotspotPrefs.setLastScan(this, now)
        connected.forEach { (mac, ip) ->
            HotspotPrefs.setIp(this, mac, ip)
            if (HotspotPrefs.getFirstSeen(this, mac) == 0L) {
                HotspotPrefs.setFirstSeen(this, mac, now)
            }
        }

        // مؤقت إيقاف نقطة الاتصال كلها
        val autoOffEnd = HotspotPrefs.getAutoOffEnd(this)
        if (autoOffEnd > 0L && now >= autoOffEnd) {
            HotspotPrefs.setAutoOffEnd(this, 0L)
            NotificationHelper.showAutoOff(this)
        }

        // لكل جهاز متصل: انتهاء الوقت + الحظر
        connected.keys.forEach { mac ->
            val label = HotspotPrefs.getLabel(this, mac)
            val limit = HotspotPrefs.getLimitMinutes(this, mac)
            if (limit > 0) {
                val firstSeen = HotspotPrefs.getFirstSeen(this, mac)
                if (firstSeen > 0L && now - firstSeen >= limit * 60_000L &&
                    !HotspotPrefs.wasNotifiedExpired(this, mac)
                ) {
                    HotspotPrefs.setNotifiedExpired(this, mac, true)
                    NotificationHelper.showTimeExpired(this, label)
                }
            }
            if (HotspotPrefs.isBlocked(this, mac) &&
                !HotspotPrefs.wasNotifiedBlocked(this, mac)
            ) {
                HotspotPrefs.setNotifiedBlocked(this, mac, true)
                NotificationHelper.showBlockedDevice(this, label, mac)
            }
        }

        // حدّث إشعار الخدمة بعدد المتصلين
        val manager = getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        manager.notify(
            NotificationHelper.MONITOR_NOTIFICATION_ID,
            NotificationHelper.foregroundNotification(this, connected.size)
        )
        broadcastChanged()
    }

    private fun broadcastChanged() {
        sendBroadcast(Intent(HotspotPrefs.ACTION_DEVICES_CHANGED))
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, HotspotMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HotspotMonitorService::class.java))
        }
    }
}
