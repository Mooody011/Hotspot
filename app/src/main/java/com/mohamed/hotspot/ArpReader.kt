package com.mohamed.hotspot

import java.io.File

/**
 * قراءة جدول ARP من /proc/net/arp (بديل لأجهزة أندرويد القديمة أو لما
 * إذن الموقع مش متاح، فالـ SoftApCallback مش هيشتغل).
 * السطور شكلها: "192.168.43.5  0x1  0x2  aa:bb:cc:dd:ee:ff  *  wlan0"
 * ممكن النظام يمنع القراءة على بعض الأجهزة، فبيرجع خريطة فاضية وقتها.
 * النتيجة: خريطة MAC (كابيتال) -> IP.
 */
object ArpReader {
    fun readMacToIp(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val lines = File("/proc/net/arp").readLines()
            for (line in lines.drop(1)) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 4) continue
                val ip = parts[0]
                val mac = parts[3].uppercase()
                if (mac == "00:00:00:00:00:00") continue
                if (!mac.contains(":")) continue
                result[mac] = ip
            }
        } catch (_: Exception) {
        }
        return result
    }
}
