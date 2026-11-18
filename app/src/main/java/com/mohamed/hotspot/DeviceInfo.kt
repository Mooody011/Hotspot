package com.mohamed.hotspot

data class DeviceInfo(
    val mac: String,
    val ip: String,
    val label: String,
    val limitMinutes: Int,
    val firstSeenMillis: Long,
    val isBlocked: Boolean
) {
    /** الوقت المتبقي بالمللي ثانية، null يعني بدون حد، و0 أو أقل يعني انتهى. */
    fun remainingMillis(nowMillis: Long): Long? {
        if (limitMinutes <= 0) return null
        if (firstSeenMillis <= 0L) return limitMinutes * 60_000L
        return limitMinutes * 60_000L - (nowMillis - firstSeenMillis)
    }

    fun isExpired(nowMillis: Long): Boolean {
        val remaining = remainingMillis(nowMillis)
        return remaining != null && remaining <= 0L
    }
}

/** "01:25:30" أو "25:10" حسب طول المدة. */
fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
