package com.mohamed.hotspot

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), DeviceAdapter.Listener {

    private lateinit var adapter: DeviceAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var autoOffMinutes = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val locationOk = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!locationOk) {
            Toast.makeText(
                this,
                "إذن الموقع لازم عشان التطبيق يشوف الأجهزة المتصلة",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val devicesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAll()
        }
    }

    private val refresher = object : Runnable {
        override fun run() {
            refreshAll()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationHelper.createChannels(this)

        val rvDevices: RecyclerView = findViewById(R.id.rvDevices)
        rvDevices.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(emptyList(), this)
        rvDevices.adapter = adapter

        requestPermissionsIfNeeded()

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener { openTetherSettings() }
        findViewById<Button>(R.id.btnOpenSettingsHidden).setOnClickListener { openTetherSettings() }

        findViewById<Button>(R.id.btnStartMonitor).setOnClickListener {
            if (!hasLocationPermission()) {
                Toast.makeText(
                    this, "إذن الموقع لازم الأول عشان المراقبة تشتغل", Toast.LENGTH_LONG
                ).show()
                requestPermissionsIfNeeded()
                return@setOnClickListener
            }
            HotspotMonitorService.start(this)
            Toast.makeText(this, "المراقبة اشتغلت", Toast.LENGTH_SHORT).show()
            refreshAll()
        }
        findViewById<Button>(R.id.btnStopMonitor).setOnClickListener {
            HotspotMonitorService.stop(this)
            HotspotPrefs.setMonitoring(this, false)
            Toast.makeText(this, "المراقبة وقفت", Toast.LENGTH_SHORT).show()
            refreshAll()
        }

        // مؤقت إيقاف نقطة الاتصال كلها
        val spAutoOff: Spinner = findViewById(R.id.spAutoOff)
        val autoOffOptions = listOf("بدون مؤقت", "15 دقيقة", "30 دقيقة", "60 دقيقة", "120 دقيقة")
        val autoOffValues = listOf(0, 15, 30, 60, 120)
        spAutoOff.adapter = ArrayAdapter(this, R.layout.item_spinner, autoOffOptions).also {
            it.setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spAutoOff.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                autoOffMinutes = autoOffValues[pos]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        findViewById<Button>(R.id.btnSetAutoOff).setOnClickListener {
            if (autoOffMinutes <= 0) {
                Toast.makeText(this, "اختار مدة الأول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HotspotPrefs.setAutoOffEnd(
                this, System.currentTimeMillis() + autoOffMinutes * 60_000L
            )
            Toast.makeText(
                this, "مؤقت الإيقاف اتفعّل: هنبّهك بعد $autoOffMinutes دقيقة", Toast.LENGTH_LONG
            ).show()
            refreshAll()
        }
        findViewById<Button>(R.id.btnCancelAutoOff).setOnClickListener {
            HotspotPrefs.setAutoOffEnd(this, 0L)
            Toast.makeText(this, "مؤقت الإيقاف اتلغى", Toast.LENGTH_SHORT).show()
            refreshAll()
        }

        // العدد اللي المستخدم عايزه يتصل (للعرض والتنبيه بس)
        val spDesiredMax: Spinner = findViewById(R.id.spDesiredMax)
        val maxOptions = (1..10).map { "$it أجهزة" }
        spDesiredMax.adapter = ArrayAdapter(this, R.layout.item_spinner, maxOptions).also {
            it.setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spDesiredMax.setSelection(
            (HotspotPrefs.getDesiredMaxClients(this) - 1).coerceIn(0, 9)
        )
        spDesiredMax.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                HotspotPrefs.setDesiredMaxClients(this@MainActivity, pos + 1)
                refreshAll()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        // أندرويد 14+ (وهدف البناء 34) بيشترط تحديد export للريسيفر المسجل برمجياً،
        // والبث هنا داخلي للتطبيق فبنستخدم NOT_EXPORTED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                devicesReceiver, IntentFilter(HotspotPrefs.ACTION_DEVICES_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                devicesReceiver, IntentFilter(HotspotPrefs.ACTION_DEVICES_CHANGED)
            )
        }
        handler.post(refresher)
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(devicesReceiver)
        } catch (_: Exception) {
        }
        handler.removeCallbacks(refresher)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (!hasLocationPermission()) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun openTetherSettings() {
        try {
            // Settings.ACTION_TETHER_SETTINGS مش موجود في الـ SDK العام،
            // فبنستخدم قيمة الأكشن نفسها كنص.
            startActivity(Intent("android.settings.TETHER_SETTINGS"))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(
                    this, "معرفتش أفتح إعدادات نقطة الاتصال", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshAll() {
        // حالة نقطة الاتصال والمراقبة
        val hotspotOn = HotspotPrefs.isHotspotOn(this)
        val tvStatus: TextView = findViewById(R.id.tvHotspotStatus)
        tvStatus.text = when (hotspotOn) {
            true -> "حالة نقطة الاتصال: شغالة"
            false -> "حالة نقطة الاتصال: مقفولة — شغّلها من الإعدادات"
            null -> "حالة نقطة الاتصال: غير معروفة — شغّل المراقبة الأول"
        }
        val monitoring = HotspotPrefs.isMonitoring(this)
        findViewById<TextView>(R.id.tvMonitoringStatus).text =
            if (monitoring) "المراقبة: شغالة" else "المراقبة: واقفة"
        findViewById<Button>(R.id.btnStartMonitor).visibility =
            if (monitoring) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btnStopMonitor).visibility =
            if (monitoring) View.VISIBLE else View.GONE

        // مؤقت الإيقاف
        val autoOffEnd = HotspotPrefs.getAutoOffEnd(this)
        val tvAutoOff: TextView = findViewById(R.id.tvAutoOffStatus)
        val btnCancel: Button = findViewById(R.id.btnCancelAutoOff)
        if (autoOffEnd > 0L) {
            val remaining = autoOffEnd - System.currentTimeMillis()
            tvAutoOff.text = if (remaining > 0) {
                "مؤقت الإيقاف شغال — هيبعت تنبيه بعد ${formatRemaining(remaining)}"
            } else {
                "مؤقت الإيقاف خلص"
            }
            btnCancel.visibility = View.VISIBLE
        } else {
            tvAutoOff.text = "مفيش مؤقت إيقاف شغال"
            btnCancel.visibility = View.GONE
        }

        // أقصى عدد أجهزة
        val maxSupported = HotspotPrefs.getMaxSupportedClients(this)
        val desired = HotspotPrefs.getDesiredMaxClients(this)
        findViewById<TextView>(R.id.tvMaxClients).text = if (maxSupported > 0) {
            "الموبايل بيدعم لحد $maxSupported جهاز — وانت عايز $desired أجهزة كحد أقصى"
        } else {
            "أقصى عدد أجهزة الموبايل: غير معروف على جهازك — وانت عايز $desired أجهزة كحد أقصى"
        }
        val connectedCount = HotspotPrefs.getConnectedMacs(this).size
        findViewById<TextView>(R.id.tvConnectedCount).text =
            "الأجهزة المتصلة دلوقتي: $connectedCount"

        // قايمة الأجهزة
        val devices = buildDeviceList()
        adapter.update(devices)
        findViewById<TextView>(R.id.tvNoDevices).visibility =
            if (devices.isEmpty()) View.VISIBLE else View.GONE
        // تنبيه لو إذن الإشعارات مقفول: تنبيهات انتهاء الوقت مش هتوصلك
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val tvNote: TextView = findViewById(R.id.tvMonitoringNote)
        when {
            !monitoring -> {
                tvNote.text = "المراقبة واقفة — دوس «بدء المراقبة» وشغّل نقطة الاتصال عشان تشوف الأجهزة الحالية."
                tvNote.visibility = View.VISIBLE
            }
            !notificationsGranted -> {
                tvNote.text = "إذن الإشعارات مقفول — فعّله من إعدادات التطبيق عشان تنبيه «وقت الجهاز خلص» يوصلك."
                tvNote.visibility = View.VISIBLE
            }
            else -> tvNote.visibility = View.GONE
        }
    }

    private fun buildDeviceList(): List<DeviceInfo> {
        val macs = HotspotPrefs.getConnectedMacs(this)
        return macs.map { mac ->
            DeviceInfo(
                mac = mac,
                ip = HotspotPrefs.getIp(this, mac),
                label = HotspotPrefs.getLabel(this, mac),
                limitMinutes = HotspotPrefs.getLimitMinutes(this, mac),
                firstSeenMillis = HotspotPrefs.getFirstSeen(this, mac),
                isBlocked = HotspotPrefs.isBlocked(this, mac)
            )
        }.sortedBy { it.label }
    }

    // --- DeviceAdapter.Listener ---

    override fun onLimitChanged(mac: String, minutes: Int) {
        HotspotPrefs.setLimitMinutes(this, mac, minutes)
        // تغيير الحد بيعيد العدّاد من دلوقتي
        HotspotPrefs.resetTimer(this, mac)
        val label = if (minutes == 0) "بدون حد" else "$minutes دقيقة"
        Toast.makeText(
            this, "حد جهاز ${HotspotPrefs.getLabel(this, mac)} بقى: $label", Toast.LENGTH_SHORT
        ).show()
        refreshAll()
    }

    override fun onBlockChanged(mac: String, blocked: Boolean) {
        HotspotPrefs.setBlocked(this, mac, blocked)
        if (blocked) {
            Toast.makeText(
                this,
                "اتحظر ${HotspotPrefs.getLabel(this, mac)} — لو متصل دلوقتي، غيّر باسورد نقطة الاتصال عشان ميدخلش تاني",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "اتفك حظر الجهاز", Toast.LENGTH_SHORT).show()
        }
        refreshAll()
    }

    override fun onRename(mac: String) {
        val input = EditText(this).apply {
            setText(HotspotPrefs.getLabel(this@MainActivity, mac))
            hint = "اسم الجهاز"
        }
        AlertDialog.Builder(this)
            .setTitle("تسمية الجهاز")
            .setMessage("MAC: $mac")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    HotspotPrefs.setLabel(this, mac, name)
                    refreshAll()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onResetTimer(mac: String) {
        HotspotPrefs.resetTimer(this, mac)
        Toast.makeText(
            this, "عدّاد جهاز ${HotspotPrefs.getLabel(this, mac)} اتصفّر", Toast.LENGTH_SHORT
        ).show()
        refreshAll()
    }
}
