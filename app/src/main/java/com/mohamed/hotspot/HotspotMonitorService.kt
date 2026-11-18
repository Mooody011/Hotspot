package com.mohamed.hotspot

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * خدمة مراقبة في المقدمة: بتتابع الأجهزة المتصلة بنقطة اتصال النظام،
 * وبتحسب الوقت لكل جهاز، وبتنبّه لما وقت جهاز يخلص أو جهاز محظور يتصل،
 * وبتنبّه لما مؤقت إيقاف نقطة الاتصال يخلص.
 *
 * حدود أندرويد الصريحة (مش قصور في التطبيق):
 * - التطبيق ميقدرش يشغّل نقطة اتصال إنترنت بنفسه، ولا يطرد جهاز متصل،
 *   ولا يفرض قايمة حظر. دي صلاحيات تطبيق الإعدادات بتاع النظام بس.
 * - اللي بيعمله التطبيق: مراقبة + تنبيه، والمستخدم هو اللي بيفصل يدوياً.
 */
class HotspotMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wifiManager: WifiManager? = null
    private var softApCallback: WifiManager.SoftApCallback? = null
    private var callbackMacs: Set<String>? = null
    private var useCallback = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkAll()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        NotificationHelper.createChannels(this)
        useCallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationHelper.MONITOR_NOTIFICATION_ID,
            NotificationHelper.foregroundNotification(this, 0)
        )
        HotspotPrefs.setMonitoring(this, true)
        registerCallbackIfNeeded()
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        checkAll()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        unregisterCallbackIfNeeded()
        HotspotPrefs.setMonitoring(this, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerCallbackIfNeeded() {
        if (!useCallback) return
        if (softApCallback != null) return
        val callback = object : WifiManager.SoftApCallback {
            override fun onStateChanged(state: Int, failureReason: Int) {
                val on = state == WifiManager.WIFI_AP_STATE_ENABLED
                HotspotPrefs.setHotspotOn(this@HotspotMonitorService, on)
                broadcastChanged()
            }

            override fun onConnectedClientsChanged(clients: List<WifiManager.WifiClient>) {
                callbackMacs = clients.map { it.macAddress.toString() }
                    .map { HotspotPrefs.normMac(it) }.toSet()
                checkAll()
            }

            override fun onCapabilityChanged(capability: WifiManager.SoftApCapability) {
                HotspotPrefs.setMaxSupportedClients(
                    this@HotspotMonitorService, capability.maxSupportedClients
                )
            }
        }
        softApCallback = callback
        try {
            val executor: Executor = mainExecutor
            wifiManager?.registerSoftApCallback(executor, callback)
        } catch (_: Exception) {
            // لو التسجيل فشل لأي سبب، بنكمّل بقراءة ARP
            softApCallback = null
            useCallback = false
        }
    }

    private fun unregisterCallbackIfNeeded() {
        val callback = softApCallback ?: return
        try {
            wifiManager?.unregisterSoftApCallback(callback)
        } catch (_: Exception) {
        }
        softApCallback = null
    }

    /** الأجهزة المتصلة دلوقتي: من الـ SoftApCallback، أو من جدول ARP كبديل. */
    private fun currentMacs(): Map<String, String> {
        val macs = callbackMacs
        if (useCallback && macs != null) {
            val arp = ArpReader.readMacToIp()
            return macs.associateWith { arp[it] ?: HotspotPrefs.getIp(this, it) }
        }
        val arp = ArpReader.readMacToIp()
        return arp
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
