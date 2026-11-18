package com.mohamed.hotspot

import android.content.Context

/**
 * كل بيانات التطبيق محفوظة محلياً في SharedPreferences:
 * اسم كل جهاز، الحد الزمني، أول مرة اتشاف فيها، قوائم الحظر، ومؤقت الإيقاف.
 * مفيش أي بيانات بتتبعت برّه الموبايل.
 */
object HotspotPrefs {
    private const val FILE = "hotspot_prefs"

    const val ACTION_DEVICES_CHANGED = "com.mohamed.hotspot.DEVICES_CHANGED"

    fun normMac(mac: String): String = mac.trim().uppercase()

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getLabel(context: Context, mac: String): String =
        prefs(context).getString("label_${normMac(mac)}", null) ?: normMac(mac)

    fun setLabel(context: Context, mac: String, label: String) {
        prefs(context).edit().putString("label_${normMac(mac)}", label.trim()).apply()
    }

    /** الحد بالدقائق، 0 يعني بدون حد. */
    fun getLimitMinutes(context: Context, mac: String): Int =
        prefs(context).getInt("limit_${normMac(mac)}", 0)

    fun setLimitMinutes(context: Context, mac: String, minutes: Int) {
        prefs(context).edit().putInt("limit_${normMac(mac)}", minutes).apply()
    }

    fun getFirstSeen(context: Context, mac: String): Long =
        prefs(context).getLong("first_${normMac(mac)}", 0L)

    fun setFirstSeen(context: Context, mac: String, timeMillis: Long) {
        prefs(context).edit().putLong("first_${normMac(mac)}", timeMillis).apply()
    }

    fun wasNotifiedExpired(context: Context, mac: String): Boolean =
        prefs(context).getBoolean("notified_${normMac(mac)}", false)

    fun setNotifiedExpired(context: Context, mac: String, value: Boolean) {
        prefs(context).edit().putBoolean("notified_${normMac(mac)}", value).apply()
    }

    fun wasNotifiedBlocked(context: Context, mac: String): Boolean =
        prefs(context).getBoolean("notified_blocked_${normMac(mac)}", false)

    fun setNotifiedBlocked(context: Context, mac: String, value: Boolean) {
        prefs(context).edit().putBoolean("notified_blocked_${normMac(mac)}", value).apply()
    }

    fun getIp(context: Context, mac: String): String =
        prefs(context).getString("ip_${normMac(mac)}", "—") ?: "—"

    fun setIp(context: Context, mac: String, ip: String) {
        prefs(context).edit().putString("ip_${normMac(mac)}", ip).apply()
    }

    fun getConnectedMacs(context: Context): Set<String> =
        prefs(context).getStringSet("connected_macs", emptySet())?.map { normMac(it) }?.toSet()
            ?: emptySet()

    fun setConnectedMacs(context: Context, macs: Set<String>) {
        prefs(context).edit()
            .putStringSet("connected_macs", macs.map { normMac(it) }.toSet())
            .apply()
    }

    fun getLastScan(context: Context): Long =
        prefs(context).getLong("last_scan", 0L)

    fun setLastScan(context: Context, timeMillis: Long) {
        prefs(context).edit().putLong("last_scan", timeMillis).apply()
    }

    fun isBlocked(context: Context, mac: String): Boolean =
        prefs(context).getStringSet("blocked_macs", emptySet())?.contains(normMac(mac)) == true

    fun setBlocked(context: Context, mac: String, blocked: Boolean) {
        val set = prefs(context).getStringSet("blocked_macs", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        if (blocked) set.add(normMac(mac)) else set.remove(normMac(mac))
        prefs(context).edit().putStringSet("blocked_macs", set).apply()
        // لو اتفك الحظر، امسح إشعار الحظر القديم عشان لو اتصل تاني ينبّه من جديد
        if (!blocked) setNotifiedBlocked(context, mac, false)
    }

    fun getBlockedMacs(context: Context): Set<String> =
        prefs(context).getStringSet("blocked_macs", emptySet())?.map { normMac(it) }?.toSet()
            ?: emptySet()

    /** إعادة ضبط عدّاد جهاز: يبدأ الوقت من دلوقتي ويمسح إشعار الانتهاء. */
    fun resetTimer(context: Context, mac: String) {
        setFirstSeen(context, mac, System.currentTimeMillis())
        setNotifiedExpired(context, mac, false)
    }

    fun isHotspotOn(context: Context): Boolean? {
        val p = prefs(context)
        return if (p.contains("hotspot_on")) p.getBoolean("hotspot_on", false) else null
    }

    fun setHotspotOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("hotspot_on", on).apply()
    }

    /** أقصى عدد أجهزة بيدعمه الموبايل حسب SoftApCapability، -1 لو غير معروف. */
    fun getMaxSupportedClients(context: Context): Int =
        prefs(context).getInt("max_supported", -1)

    fun setMaxSupportedClients(context: Context, value: Int) {
        prefs(context).edit().putInt("max_supported", value).apply()
    }

    /** العدد اللي المستخدم عايزه يتصل، للعرض والتنبيه بس (التطبيق ميقدرش يفرضه). */
    fun getDesiredMaxClients(context: Context): Int =
        prefs(context).getInt("max_clients_desired", 8)

    fun setDesiredMaxClients(context: Context, value: Int) {
        prefs(context).edit().putInt("max_clients_desired", value).apply()
    }

    /** وقت انتهاء مؤقت إيقاف نقطة الاتصال (millis)، 0 يعني مفيش مؤقت شغال. */
    fun getAutoOffEnd(context: Context): Long =
        prefs(context).getLong("auto_off_end", 0L)

    fun setAutoOffEnd(context: Context, timeMillis: Long) {
        prefs(context).edit().putLong("auto_off_end", timeMillis).apply()
    }

    fun isMonitoring(context: Context): Boolean =
        prefs(context).getBoolean("monitoring", false)

    fun setMonitoring(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("monitoring", value).apply()
    }
}
