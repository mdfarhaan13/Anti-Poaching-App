package com.example.antipoaching

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var selectedVideoUri: Uri? = null
    private lateinit var tvVideoPath: TextView
    private lateinit var tvStatus: TextView

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) allGranted = false
        }
        if (!allGranted) {
            Toast.makeText(this, "Permissions required to function.", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            tvVideoPath.text = uri.path
            tvStatus.text = "🎬 Video selected. Ready to start."
            tvStatus.setTextColor(android.graphics.Color.BLUE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvVideoPath = findViewById(R.id.tvVideoPath)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnBrowseVideo).setOnClickListener {
            selectVideoLauncher.launch("video/mp4")
        }

        findViewById<Button>(R.id.btnStartCamera).setOnClickListener {
            if (hasPermissions()) {
                val intent = Intent(this, CameraDetectionActivity::class.java)
                startActivity(intent)
            } else {
                requestPermissions()
            }
        }

        findViewById<Button>(R.id.btnStartVideo).setOnClickListener {
            if (selectedVideoUri == null) {
                tvStatus.text = "❗ Please select a video first."
                tvStatus.setTextColor(android.graphics.Color.rgb(255, 165, 0)) // Orange
            } else {
                if (hasPermissions()) {
                    val intent = Intent(this, VideoDetectionActivity::class.java)
                    intent.putExtra("videoUri", selectedVideoUri.toString())
                    intent.data = selectedVideoUri
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                } else {
                    requestPermissions()
                }
            }
        }
        
        requestPermissions()
    }

    private fun hasPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        return camera && storage
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}
