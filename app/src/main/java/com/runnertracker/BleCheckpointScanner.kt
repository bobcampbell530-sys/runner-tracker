package com.runnertracker

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * BLE Checkpoint Scanner
 * 
 * Scans for BLE beacon tags (iBeacon/Eddystone) and posts checkpoint
 * events to the server when a known runner's tag is detected.
 */
class BleCheckpointScanner(private val context: Context) {

    private val TAG = "BLE_SCAN"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private var serverUrl = ""
    private val tagMap = mutableMapOf<String, String>()
    private val lastSeen = mutableMapOf<String, Long>()
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var onLog: ((String) -> Unit)? = null
    private var onDetection: ((bib: String, tagId: String, rssi: Int) -> Unit)? = null
    private var onStateChange: ((scanning: Boolean) -> Unit)? = null

    fun setOnLog(callback: (String) -> Unit) { onLog = callback }
    fun setOnDetection(callback: (bib: String, tagId: String, rssi: Int) -> Unit) { onDetection = callback }
    fun setOnStateChange(callback: (scanning: Boolean) -> Unit) { onStateChange = callback }

    fun loadTagMap(json: String) {
        tagMap.clear()
        try {
            val jsonObj = JSONObject(json)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                tagMap[key] = jsonObj.getString(key)
            }
            log("✅ Loaded ${tagMap.size} tag mappings")
        } catch (e: Exception) {
            log("❌ Failed to parse tag map: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun start(server: String) {
        serverUrl = server.trim().removeSuffix("/")
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null) {
            log("❌ Bluetooth not available on this device")
            return
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            log("❌ Bluetooth is disabled — turn it on")
            return
        }
        
        scanner = bluetoothAdapter!!.bluetoothLeScanner
        
        if (scanner == null) {
            log("❌ BLE scanner not available")
            return
        }
        
        if (isScanning) {
            log("⚠️ Already scanning")
            return
        }
        
        log("📡 Starting BLE checkpoint scanner...")
        log("   Server: $serverUrl")
        log("   Tags mapped: ${tagMap.size}")
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }
            
            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    handleScanResult(result)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                log("❌ BLE scan failed: error $errorCode")
                stop()
            }
        }
        
        try {
            scanner!!.startScan(scanCallback)
            isScanning = true
            onStateChange?.invoke(true)
            log("✅ BLE scanning active — waiting for tags...")
        } catch (e: Exception) {
            log("❌ BLE scan start failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isScanning) return
        
        log("⏹ Stopping BLE scanner...")
        
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
            log("❌ BLE stop failed: ${e.message}")
        }
        
        isScanning = false
        scanCallback = null
        onStateChange?.invoke(false)
        log("✅ BLE scanner stopped")
    }

    fun isScanning(): Boolean = isScanning

    private fun handleScanResult(result: ScanResult) {
        if (!isScanning) return
        
        val device = result.device
        val rssi = result.rssi
        val tagId = device.address
        
        val manufacturerData = result.scanRecord?.manufacturerSpecificData
        val tagIdentity = extractTagIdentity(manufacturerData, tagId)
        
        val bib = tagMap[tagIdentity]
        if (bib == null) {
            // Not in our mapping — skip silently
            return
        }
        
        val now = System.currentTimeMillis()
        val last = lastSeen[tagIdentity] ?: 0L
        if (now - last < 10_000L) return
        
        lastSeen[tagIdentity] = now
        
        log("📍 Tag detected: $tagIdentity → $bib (RSSI: $rssi)")
        
        val location = getLastKnownLocation()
        sendCheckpoint(bib, tagIdentity, rssi, location)
        onDetection?.invoke(bib, tagIdentity, rssi)
    }

    private fun extractTagIdentity(manufacturerData: SparseArray<ByteArray>?, fallback: String): String {
        if (manufacturerData == null) return "MAC:$fallback"
        
        // Check for Apple (iBeacon) - manufacturer ID 0x004C (76)
        val appleData = manufacturerData.get(0x004C)
        if (appleData != null && appleData.size >= 22) {
            // iBeacon: 02 01 [UUID:16] [major:2] [minor:2] [txPower:1]
            if ((appleData[0].toInt() and 0xFF) == 0x02 && (appleData[1].toInt() and 0xFF) == 0x01) {
                val uuid = bytesToHex(appleData, 2, 16)
                val major = ((appleData[18].toInt() and 0xFF) shl 8) or (appleData[19].toInt() and 0xFF)
                val minor = ((appleData[20].toInt() and 0xFF) shl 8) or (appleData[21].toInt() and 0xFF)
                return "IB:$uuid:$major:$minor"
            }
            
            // Eddystone UID: frame control 0x20
            if ((appleData[0].toInt() and 0xFF) == 0x20 && appleData.size >= 17) {
                val namespace = bytesToHex(appleData, 1, 10)
                val instance = bytesToHex(appleData, 11, 6)
                return "ED:$namespace:$instance"
            }
        }
        
        return "MAC:$fallback"
    }

    private fun bytesToHex(data: ByteArray, start: Int, length: Int): String {
        val result = StringBuilder()
        for (i in start until start + length) {
            if (i < data.size) {
                result.append(String.format("%02x", data[i].toInt() and 0xFF))
            }
        }
        return result.toString()
    }

    private fun getLastKnownLocation(): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            when {
                lastGps != null && System.currentTimeMillis() - lastGps.time < 60_000 -> lastGps
                lastNet != null -> lastNet
                else -> lastGps
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendCheckpoint(bib: String, tagId: String, rssi: Int, location: Location?) {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val checkpointUrl = "$cleanUrl/api/checkpoint"
        
        networkExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                val json = JSONObject().apply {
                    put("name", bib)
                    put("station", "BLE-Checkpoint")
                    put("ts", System.currentTimeMillis() / 1000.0)
                    put("tag", tagId)
                    put("rssi", rssi)
                    
                    location?.let {
                        put("lat", it.latitude)
                        put("lng", it.longitude)
                    }
                }
                
                val url = URL(checkpointUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true
                
                connection.outputStream.write(json.toString().toByteArray(Charsets.UTF_8))
                val responseCode = connection.responseCode
                
                Log.e(TAG, "Checkpoint POST: $bib @ BLE ($responseCode)")
                
                if (responseCode in 200..299) {
                    mainHandler.post {
                        onLog?.invoke("✅ Checkpoint sent: $bib ($tagId)")
                    }
                } else {
                    mainHandler.post {
                        onLog?.invoke("❌ Checkpoint failed: $responseCode")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Checkpoint error: ${e.message}", e)
                mainHandler.post {
                    onLog?.invoke("❌ Checkpoint error: ${e.message}")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun log(msg: String) {
        Log.e(TAG, msg)
        mainHandler.post {
            onLog?.invoke(msg)
        }
    }
}
