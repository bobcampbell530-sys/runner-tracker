package com.runnertracker

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.util.SparseArray
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class TrackingService : Service() {

    private val binder = LocalBinder()
    private lateinit var locationManager: LocationManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private var isRunning = false
    private var bibNumber = ""
    private var serverUrl = ""
    private var pingCount = 0
    private var lastPingElapsedMs = 0L
    private val pingIntervalMs = 20_000L
    private val networkExecutor = Executors.newSingleThreadExecutor()

    // BLE scanning
    private var bleScanner: BluetoothLeScanner? = null
    private var bleCallback: ScanCallback? = null
    private var bleScanning = false
    private val tagMap = mutableMapOf<String, String>()  // tagId -> bib
    private val lastTagSeen = mutableMapOf<String, Long>()
    private var lastLocation: Location? = null

    /*
     * Location callback — Android calls this with fresh GPS positions.
     * We rate-limit server uploads to once per 20 seconds.
     */
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.e(TAG, "📍 GPS fix: ${location.latitude}, ${location.longitude}, acc=${location.accuracy}m, age=${locationAgeMs(location)}ms")

            if (!isRunning) return

            // Reject stale locations (>60s old)
            if (locationAgeMs(location) > MAX_LOCATION_AGE_MS) {
                Log.w(TAG, "Ignoring stale location: ${locationAgeMs(location)}ms old")
                return
            }

            // Store latest location for BLE proximity events
            lastLocation = location

            // Rate-limit to one ping per 20 seconds
            val now = SystemClock.elapsedRealtime()
            if (lastPingElapsedMs != 0L && now - lastPingElapsedMs < pingIntervalMs) {
                Log.e(TAG, "Ping throttled (${now - lastPingElapsedMs}ms since last)")
                return
            }

            lastPingElapsedMs = now
            pingCount++
            Log.e(TAG, "Sending ping #$pingCount")
            sendPing(location)
        }

        override fun onProviderEnabled(provider: String) {
            Log.e(TAG, "Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.e(TAG, "Provider disabled: $provider")
            if (provider == LocationManager.GPS_PROVIDER) {
                showToast("GPS disabled — turn it on")
            }
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.e(TAG, "GPS status: $provider → $status")
        }
    }

    private fun locationAgeMs(location: Location): Long {
        return try {
            (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        } catch (e: Exception) {
            0L
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "=== SERVICE CREATED ===")
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                bibNumber = intent.getStringExtra(EXTRA_BIB) ?: bibNumber
                serverUrl = intent.getStringExtra(EXTRA_URL) ?: serverUrl
                val tagMapJson = intent.getStringExtra(EXTRA_TAGMAP) ?: ""
                Log.e(TAG, "bib=$bibNumber url=$serverUrl tagmap=${tagMapJson.length} chars")
                if (tagMapJson.isNotEmpty()) {
                    loadTagMap(tagMapJson)
                }
                startTracking()
            }
            ACTION_STOP -> {
                Log.e(TAG, "Stop requested")
                stopTracking()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean = true

    override fun onDestroy() {
        Log.e(TAG, "=== SERVICE DESTROYED ===")
        stopTracking()
        networkExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.e(TAG, "Task removed — relying on START_STICKY to recreate")
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Load the tag-to-bib mapping.
     * JSON format: { "IB:uuid:1:1": "Bib 142", "MAC:AA:BB:CC:DD:EE:01": "Bib 008" }
     */
    private fun loadTagMap(json: String) {
        tagMap.clear()
        try {
            val jsonObj = JSONObject(json)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                tagMap[key] = jsonObj.getString(key)
            }
            Log.e(TAG, "✅ Loaded ${tagMap.size} BLE tag mappings")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse tag map: ${e.message}")
        }
    }

    private fun startTracking() {
        if (isRunning) {
            Log.e(TAG, "Already running")
            return
        }

        Log.e(TAG, "=== STARTING TRACKING ===")

        // Check GPS enabled BEFORE doing anything
        if (!isGpsEnabled()) {
            Log.e(TAG, "GPS is disabled")
            showToast("GPS is OFF — turn it on in Quick Settings")
            return
        }
        Log.e(TAG, "GPS is enabled")

        // Start foreground service FIRST (Android requirement)
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.e(TAG, "✅ startForeground SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: startForeground failed: ${e.message}", e)
            showToast("FATAL: ${e.message}")
            stopSelf()
            return
        }

        // Acquire wake lock to keep CPU alive when screen is off
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunnerTracker::WakeLock")
        wakeLock.acquire()
        Log.e(TAG, "Wake lock acquired")

        // Validate config
        if (bibNumber.isBlank()) {
            Log.e(TAG, "No bib number")
            showToast("No bib number")
            stopTracking()
            return
        }
        if (serverUrl.isBlank()) {
            Log.e(TAG, "No server URL")
            showToast("No server URL")
            stopTracking()
            return
        }

        // Check internet (warn but don't fail — cellular may drop)
        if (!hasInternet()) {
            Log.w(TAG, "No internet — GPS tracking will continue")
            showToast("No internet yet — GPS tracking running")
        } else {
            Log.e(TAG, "Internet OK")
        }

        // Now it's safe to mark as running
        isRunning = true
        pingCount = 0
        lastPingElapsedMs = 0L

        // Register for continuous GPS updates
        try {
            Log.e(TAG, "Registering GPS listener (1s interval, 0m distance)")
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_MIN_TIME_MS,
                GPS_MIN_DISTANCE_METERS,
                locationListener
            )
            Log.e(TAG, "✅ GPS listener registered")
            showToast("🏃 Tracking started — waiting for GPS fix...")
        } catch (e: SecurityException) {
            Log.e(TAG, "GPS permission error", e)
            showToast("GPS permission denied")
            isRunning = false
            stopTracking()
        } catch (e: Exception) {
            Log.e(TAG, "GPS listener error: ${e.message}")
            showToast("GPS error: ${e.message}")
            isRunning = false
            stopTracking()
        }

        // Start BLE scanning (if tag map is loaded)
        startBleScanning()
    }

    /**
     * Start BLE scanning for other runners' tags.
     * Each detection sends a proximity event to the server.
     */
    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        if (tagMap.isEmpty()) {
            Log.e(TAG, "No tag map loaded — BLE scanning disabled")
            return
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            Log.e(TAG, "❌ Bluetooth not available")
            return
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "❌ Bluetooth disabled — BLE scanning skipped")
            showToast("Bluetooth OFF — BLE detection disabled")
            return
        }

        bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            Log.e(TAG, "❌ BLE scanner not available")
            return
        }

        bleCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleBleResult(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    handleBleResult(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "❌ BLE scan failed: error $errorCode")
                stopBleScanning()
            }
        }

        try {
            bleScanner!!.startScan(bleCallback)
            bleScanning = true
            Log.e(TAG, "✅ BLE scanning active — ${tagMap.size} tags to detect")
            showToast("📡 BLE scanning active")
        } catch (e: Exception) {
            Log.e(TAG, "❌ BLE scan start failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanning() {
        if (!bleScanning) return
        try {
            bleCallback?.let { bleScanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.e(TAG, "❌ BLE stop failed: ${e.message}")
        }
        bleScanning = false
        bleCallback = null
        Log.e(TAG, "⏹ BLE scanning stopped")
    }

    private fun handleBleResult(result: ScanResult) {
        if (!isRunning) return

        val device = result.device
        val rssi = result.rssi
        val tagId = device.address
        val manufacturerData = result.scanRecord?.manufacturerSpecificData
        val tagIdentity = extractTagIdentity(manufacturerData, tagId)

        // Look up bib number
        val otherBib = tagMap[tagIdentity] ?: return

        // Don't detect our own tag
        if (otherBib == bibNumber) return

        // De-dupe: 30s cooldown per tag
        val now = System.currentTimeMillis()
        val last = lastTagSeen[tagIdentity] ?: 0L
        if (now - last < 30_000L) return
        lastTagSeen[tagIdentity] = now

        Log.e(TAG, "📡 BLE: $bibNumber saw $otherBib (tag=$tagIdentity, rssi=$rssi)")
        showToast("📡 Saw $otherBib nearby!")

        sendProximity(otherBib, rssi)
    }

    private fun extractTagIdentity(manufacturerData: SparseArray<ByteArray>?, fallback: String): String {
        if (manufacturerData == null) return "MAC:$fallback"

        // iBeacon (Apple - manufacturer ID 0x004C = 76)
        val appleData = manufacturerData.get(0x004C)
        if (appleData != null && appleData.size >= 22) {
            if ((appleData[0].toInt() and 0xFF) == 0x02 && (appleData[1].toInt() and 0xFF) == 0x01) {
                val uuid = bytesToHex(appleData, 2, 16)
                val major = ((appleData[18].toInt() and 0xFF) shl 8) or (appleData[19].toInt() and 0xFF)
                val minor = ((appleData[20].toInt() and 0xFF) shl 8) or (appleData[21].toInt() and 0xFF)
                return "IB:$uuid:$major:$minor"
            }
            // Eddystone UID
            if ((appleData[0].toInt() and 0xFF) == 0x20 && appleData.size >= 17) {
                val namespace = bytesToHex(appleData, 1, 10)
                val instance = bytesToHex(appleData, 11, 6)
                return "ED:$namespace:$instance"
            }
        }

        return "MAC:$fallback"
    }

    private fun bytesToHex(data: ByteArray, start: Int, length: Int): String {
        val sb = StringBuilder()
        for (i in start until start + length) {
            if (i < data.size) {
                sb.append(String.format("%02x", data[i].toInt() and 0xFF))
            }
        }
        return sb.toString()
    }

    /**
     * Send a proximity event: "I (bibNumber) detected the BLE tag of otherBib"
     */
    private fun sendProximity(otherBib: String, rssi: Int) {
        if (serverUrl.isBlank()) return
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val url = "$cleanUrl/api/proximity"
        val loc = lastLocation

        networkExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                Log.e(TAG, "POST $url (proximity)")
                val json = JSONObject().apply {
                    put("seen_by", bibNumber)
                    put("seen", otherBib)
                    put("rssi", rssi)
                    put("ts", System.currentTimeMillis() / 1000.0)
                    loc?.let {
                        put("lat", it.latitude)
                        put("lng", it.longitude)
                    }
                }

                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true

                connection.outputStream.write(json.toString().toByteArray(Charsets.UTF_8))
                val responseCode = connection.responseCode
                Log.e(TAG, "Proximity response: $responseCode")

                if (responseCode in 200..299) {
                    Log.e(TAG, "✅ Proximity sent: $bibNumber → $otherBib")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Proximity error: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun stopTracking() {
        Log.e(TAG, "=== STOPPING TRACKING ===")
        isRunning = false

        // Stop BLE scanning
        stopBleScanning()

        try { locationManager.removeUpdates(locationListener) } catch (e: Exception) { }

        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
            Log.e(TAG, "Wake lock released")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopForeground failed", e)
        }

        stopSelf()
        Log.e(TAG, "=== STOPPED ===")
    }

    private fun isGpsEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    private fun hasInternet(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun sendPing(location: Location) {
        if (serverUrl.isBlank() || bibNumber.isBlank()) return

        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val pingUrl = "$cleanUrl/api/ping"

        networkExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                Log.e(TAG, "POST $pingUrl")
                val url = URL(pingUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true

                val json = JSONObject().apply {
                    put("name", bibNumber)
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("alt", location.altitude)
                    put("acc", location.accuracy)
                    put("ts", System.currentTimeMillis() / 1000.0)
                }

                connection.outputStream.write(json.toString().toByteArray(Charsets.UTF_8))
                val responseCode = connection.responseCode

                Log.e(TAG, "Response: $responseCode (${if (responseCode == 200) "✅" else "❌"})")

                if (responseCode in 200..299) {
                    val coord = "${location.latitude.toString().take(6)}, ${location.longitude.toString().take(6)}"
                    showToast("✅ Ping #$pingCount sent: $coord")
                } else {
                    showToast("Ping failed: $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ping error: ${e.message}", e)
                showToast("Network error: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showToast(message: String) {
        Handler(mainLooper).post {
            if (isRunning) {
                Toast.makeText(this@TrackingService, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bleStatus = if (bleScanning) " • 📡 BLE active" else ""
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🏃 Running — $bibNumber")
            .setContentText("GPS Active • Sending every 20s$bleStatus")
            .setSmallIcon(R.drawable.ic_notify_small)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Runner Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "TRACK"
        const val CHANNEL_ID = "runner_tracker_channel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_BIB = "EXTRA_BIB"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_TAGMAP = "EXTRA_TAGMAP"

        private const val GPS_MIN_TIME_MS = 1_000L
        private const val GPS_MIN_DISTANCE_METERS = 0f
        private const val MAX_LOCATION_AGE_MS = 60_000L
        private const val NOTIFICATION_ID = 1

        fun startService(context: Context, bib: String, url: String, tagMapJson: String = "") {
            Log.e(TAG, "startService() bib=$bib url=$url tagmap=${tagMapJson.length} chars")
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BIB, bib)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TAGMAP, tagMapJson)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            Log.e(TAG, "stopService()")
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isRunning(context: Context): Boolean {
            @Suppress("DEPRECATION")
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (TrackingService::class.java.name == service.service.className) return true
            }
            return false
        }
    }
}
