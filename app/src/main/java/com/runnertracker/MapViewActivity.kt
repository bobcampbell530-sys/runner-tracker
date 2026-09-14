package com.runnertracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Live map view — opens the server's map page in the system browser.
 * Shows all runners' positions in real-time.
 */
class MapViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the server URL passed from MainActivity
        val serverUrl = intent.getStringExtra("SERVER_URL")

        if (serverUrl.isNullOrEmpty()) {
            Toast.makeText(this, "No server URL set", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val mapUrl = "$cleanUrl/"

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open URL: ${e.message}", Toast.LENGTH_LONG).show()
        }

        finish()
    }
}
