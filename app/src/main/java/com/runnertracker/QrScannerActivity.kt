package com.runnertracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class QrScannerActivity : AppCompatActivity() {

    private val barcodeScanner = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleQrResult(result.contents)
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Point camera at the QR code")
            setCameraId(0)
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        
        barcodeScanner.launch(options)
    }

    private fun handleQrResult(rawValue: String) {
        var serverUrl = ""
        var bib = ""
        
        if (rawValue.startsWith("runnertracker://")) {
            val uri = android.net.Uri.parse(rawValue)
            serverUrl = uri.getQueryParameter("url") ?: ""
            bib = uri.getQueryParameter("bib") ?: ""
        } else if (rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
            serverUrl = rawValue
            bib = "008"
        }
        
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Not a valid QR code", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Save to SharedPreferences (the reliable backup)
        getSharedPreferences("config", MODE_PRIVATE).edit().apply {
            putString("server_url", serverUrl)
            putString("bib", bib)
            putLong("ts", System.currentTimeMillis())
            apply()
        }
        
        Toast.makeText(this, "✅ URL and bib saved\nTap back", Toast.LENGTH_LONG).show()
        
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra("server_url", serverUrl)
            putExtra("bib", bib)
        })
        
        finish()
    }
}
