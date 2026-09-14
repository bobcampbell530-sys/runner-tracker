package com.runnertracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etBibNumber: EditText
    private lateinit var etServerUrl: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnScanQr: Button
    private lateinit var tvLog: TextView
    private lateinit var btnCheckpoint: Button
    private lateinit var btnViewMap: Button
    private lateinit var etTagMap: EditText

    private var bibInput = ""
    private var urlInput = ""
    private val logMessages = mutableListOf<String>()
    private var bleScanner: BleCheckpointScanner? = null
    private val REQUEST_BLUETOOTH_PERMISSION = 2001
    
    private var qrTimestamp = 0L
    
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        addLog("QR: code=${result.resultCode}")
        
        val configPrefs = getSharedPreferences("config", MODE_PRIVATE)
        val serverUrl = configPrefs.getString("server_url", "") ?: ""
        val bib = configPrefs.getString("bib", "") ?: ""
        val scanTs = configPrefs.getLong("ts", 0L)
        
        addLog("QR url=$serverUrl bib=$bib ts=$scanTs")
        
        if (serverUrl.isNotEmpty()) {
            etServerUrl.setText(serverUrl)
            addLog("✅ URL set: $serverUrl")
        }
        if (bib.isNotEmpty()) {
            etBibNumber.setText(bib)
            addLog("✅ Bib set: $bib")
        }
        if (serverUrl.isNotEmpty() && bib.isNotEmpty()) {
            qrTimestamp = scanTs
            addLog("✅ QR scan done")
            Toast.makeText(this, "QR code scanned — URL and bib filled in", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBibNumber = findViewById(R.id.etBibNumber)
        etServerUrl = findViewById(R.id.etServerUrl)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnScanQr = findViewById(R.id.btnScanQr)
        tvLog = findViewById(R.id.tvLog)
        btnCheckpoint = findViewById(R.id.btnCheckpoint)
        btnViewMap = findViewById(R.id.btnViewMap)
        etTagMap = findViewById(R.id.etTagMap)

        // QR scanner button
        btnScanQr.setOnClickListener {
            addLog("Opening QR scanner...")
            qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }

        // Checkpoint scanner button
        btnCheckpoint.setOnClickListener {
            toggleCheckpointScanner()
        }

        // Live map button
        btnViewMap.setOnClickListener {
            val urlText = etServerUrl.text.toString().trim()
            val savedUrl = getPreferences(MODE_PRIVATE).getString("server_url", "")
            val url = if (urlText.isNotEmpty()) urlText else savedUrl
            if (url != null && url.isNotEmpty()) {
                val intent = Intent(this, MapViewActivity::class.java)
                intent.putExtra("SERVER_URL", url)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Enter server URL first", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle deep link (if user opens app via runnertracker:// URL)
        val uri = intent.data
        val deepLinkBib = uri?.getQueryParameter("bib") ?: ""
        val deepLinkUrl = uri?.getQueryParameter("url") ?: ""

        if (deepLinkBib.isNotEmpty() || deepLinkUrl.isNotEmpty()) {
            if (deepLinkBib.isNotEmpty()) {
                etBibNumber.setText(deepLinkBib)
                addLog("QR bib: $deepLinkBib")
            }
            if (deepLinkUrl.isNotEmpty()) {
                etServerUrl.setText(deepLinkUrl)
                addLog("QR URL: $deepLinkUrl")
                addLog("✅ Config loaded from QR code")
                Toast.makeText(this, "Config loaded from QR code", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener { startTracking() }
        btnStop.setOnClickListener { stopTracking() }

        addLog("App started")
        addLog("Android ${Build.VERSION.RELEASE}")
        checkAllPermissions()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data
        val bib = uri?.getQueryParameter("bib") ?: intent?.getStringExtra("bib")
        val url = uri?.getQueryParameter("url") ?: intent?.getStringExtra("url")

        if (bib != null || url != null) {
            if (bib != null) {
                etBibNumber.setText(bib)
                addLog("QR bib: $bib")
            }
            if (url != null) {
                etServerUrl.setText(url)
                addLog("QR URL: $url")
            }
            if (bib != null && url != null) {
                addLog("✅ Config updated from QR code")
                Toast.makeText(this, "Config updated from QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if we just did a QR scan (within last 5 seconds)
        val configPrefs = getSharedPreferences("config", MODE_PRIVATE)
        val scanTs = configPrefs.getLong("ts", 0L)
        val now = System.currentTimeMillis()
        val justScanned = scanTs > 0 && (now - scanTs) < 5000
        
        // Restore saved preferences
        val prefs = getPreferences(MODE_PRIVATE)
        val savedBib = prefs.getString("bib", "")
        val savedUrl = prefs.getString("server_url", "")
        
        if (justScanned) {
            // QR scan was just done — use fresh values
            val serverUrl = configPrefs.getString("server_url", "") ?: ""
            val bib = configPrefs.getString("bib", "") ?: ""
            if (serverUrl.isNotEmpty()) etServerUrl.setText(serverUrl)
            if (bib.isNotEmpty()) etBibNumber.setText(bib)
            addLog("✅ QR resume: url=$serverUrl bib=$bib")
        } else {
            // No fresh scan — restore saved values
            if (savedBib != null) etBibNumber.setText(savedBib)
            if (savedUrl != null) etServerUrl.setText(savedUrl)
        }
        
        val savedTagMap = prefs.getString("tag_map", "")
        if (savedTagMap != null) etTagMap.setText(savedTagMap)
        
        bibInput = etBibNumber.text.toString().trim()
        urlInput = etServerUrl.text.toString().trim()

        val running = TrackingService.isRunning(this)
        checkAllPermissions()
        
        if (running) {
            btnStart.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
            tvStatus.text = "📍 Tracking $bibInput"
            tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
        } else {
            btnStart.visibility = View.VISIBLE
            btnStop.visibility = View.GONE
            tvStatus.text = "Ready"
            tvStatus.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
        }
    }

    private fun addLog(msg: String) {
        val time = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        logMessages.add("$time $msg")
        if (logMessages.size > 100) logMessages.removeAt(0)
        tvLog.text = logMessages.joinToString("\n")
    }

    private fun checkAllPermissions() {
        addLog("Checking permissions...")
        
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        val hasFineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        val hasBgLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }

        addLog("Notifications: ${if (hasNotif) "✅" else "❌"}")
        addLog("Location (fine): ${if (hasFineLoc) "✅" else "❌"}")
        addLog("Location (background): ${if (hasBgLoc) "✅" else "❌"}")
        addLog("Bluetooth: ${if (hasBluetooth) "✅" else "❌"}")
        
        if (!hasNotif) addLog("  → Need notification permission")
        if (!hasFineLoc) addLog("  → Need location permission")
        if (!hasBgLoc) addLog("  → Need background location (Android 10+)")
        if (!hasBluetooth) addLog("  → Need Bluetooth permission")
    }

    private fun hasAllPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun fixUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u
            etServerUrl.setText(u)
            addLog("⚠️ Added https:// to URL")
        }
        return u
    }

    private fun startTracking() {
        bibInput = etBibNumber.text.toString().trim()
        urlInput = fixUrl(etServerUrl.text.toString().trim())
        val tagMapJson = etTagMap.text.toString().trim()

        if (bibInput.isEmpty()) {
            Toast.makeText(this, "Enter bib number", Toast.LENGTH_LONG).show()
            addLog("❌ No bib number")
            return
        }

        if (urlInput.isEmpty()) {
            Toast.makeText(this, "Enter server URL", Toast.LENGTH_LONG).show()
            addLog("❌ No server URL")
            return
        }

        if (TrackingService.isRunning(this)) {
            Toast.makeText(this, "Already tracking", Toast.LENGTH_SHORT).show()
            addLog("❌ Already running")
            return
        }

        if (!hasAllPermissions()) {
            addLog("❌ Permissions not granted")
            
            val missing = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    missing.add("Notifications → Allow")
                }
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing.add("Location → Allow")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    missing.add("Location → Allow all the time")
                }
            }
            
            AlertDialog.Builder(this)
                .setTitle("⚠️ Permissions Required")
                .setMessage("Turn these ON in Settings:\n\n${missing.joinToString("\n")}\n\nThen return and tap START")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        setData(Uri.parse("package:$packageName"))
                    }
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
            return
        }

        addLog("✅ Starting tracking for $bibInput")
        addLog("   URL: $urlInput")
        if (tagMapJson.isNotEmpty()) {
            addLog("   BLE tag map: ${tagMapJson.length} chars")
        }

        val prefs = getPreferences(MODE_PRIVATE)
        prefs.edit().putString("bib", bibInput).apply()
        prefs.edit().putString("server_url", urlInput).apply()
        prefs.edit().putString("tag_map", tagMapJson).apply()

        try {
            TrackingService.startService(this, bibInput, urlInput, tagMapJson)
        } catch (e: Exception) {
            addLog("   ❌ Service start FAILED: ${e.message}")
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        btnStart.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        tvStatus.text = "📍 Tracking $bibInput"
        tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))

        requestBatteryOptimizationExemption()
    }

    private fun stopTracking() {
        addLog("Stopping tracking...")
        TrackingService.stopService(this)
        
        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        tvStatus.text = "Stopped"
        tvStatus.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
        
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Settings Required")
                    .setMessage("To keep GPS tracking running:\n\n1. Tap 'Allow' below\n2. If a second screen appears, tap 'Allow'\n\nOr manually: Settings → Apps → Runner Tracker → Battery → Unrestricted")
                    .setPositiveButton("Allow") { _, _ ->
                        val intent = Intent().apply {
                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            data = Uri.parse("package:$packageName")
                        }
                        batteryOptLauncher.launch(intent)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * Toggle BLE checkpoint scanner mode
     */
    private fun toggleCheckpointScanner() {
        if (bleScanner != null && bleScanner!!.isScanning()) {
            // Stop scanning
            bleScanner?.stop()
            bleScanner = null
            btnCheckpoint.text = "📡 CHECKPOINT SCANNER"
            btnCheckpoint.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.button_blue)
            )
            tvStatus.text = "Ready"
            tvStatus.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
            addLog("Checkpoint scanner stopped")
            return
        }

        // Start scanning
        val url = fixUrl(etServerUrl.text.toString().trim())
        
        if (url.isEmpty()) {
            Toast.makeText(this, "Enter server URL first", Toast.LENGTH_LONG).show()
            addLog("❌ No server URL for checkpoint scanner")
            return
        }

        // Check Bluetooth permission
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        // Check if tag map is loaded
        val tagMapJson = etTagMap.text.toString().trim()
        if (tagMapJson.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Tag Map Required")
                .setMessage("Enter the tag-to-bib mapping JSON in the Tag Map field.\n\nExample:\n{\"IB:uuid123:1:1\": \"Bib 142\"}\n\nOr leave empty to scan all tags (will show MAC addresses).")
                .setPositiveButton("Continue Anyway") { _, _ ->
                    startBleScanner(url, "")
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        startBleScanner(url, tagMapJson)
    }

    private fun startBleScanner(url: String, tagMapJson: String) {
        addLog("📡 Starting BLE checkpoint scanner...")
        
        bleScanner = BleCheckpointScanner(this).apply {
            setOnLog { msg -> addLog(msg) }
            setOnDetection { bib, tagId, rssi ->
                addLog("📍 DETECTED: $bib (tag: $tagId, RSSI: $rssi)")
                Toast.makeText(this@MainActivity, "✅ $bib detected!", Toast.LENGTH_SHORT).show()
            }
            setOnStateChange { scanning ->
                runOnUiThread {
                    if (scanning) {
                        btnCheckpoint.text = "⏹ STOP SCANNING"
                        btnCheckpoint.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this@MainActivity, R.color.button_red)
                        )
                        tvStatus.text = "📡 Scanning for BLE tags..."
                        tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_light, theme))
                    } else {
                        btnCheckpoint.text = "📡 CHECKPOINT SCANNER"
                        btnCheckpoint.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this@MainActivity, R.color.button_blue)
                        )
                        tvStatus.text = "Ready"
                        tvStatus.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                    }
                }
            }
        }
        
        if (tagMapJson.isNotEmpty()) {
            bleScanner?.loadTagMap(tagMapJson)
        }
        
        bleScanner?.start(url)
    }

    private fun requestBluetoothPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                addLog("✅ Bluetooth permission granted")
                // Retry starting the scanner
                val url = fixUrl(etServerUrl.text.toString().trim())
                val tagMapJson = etTagMap.text.toString().trim()
                startBleScanner(url, tagMapJson)
            } else {
                addLog("❌ Bluetooth permission denied")
                Toast.makeText(this, "Bluetooth permission required for checkpoint scanner", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Battery optimization check launcher
    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            addLog("Battery: ${if (pm.isIgnoringBatteryOptimizations(packageName)) "disabled ✅" else "still active ❌"}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleScanner?.stop()
        bleScanner = null
    }
}
