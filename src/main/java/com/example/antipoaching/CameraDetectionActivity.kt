package com.example.antipoaching

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraDetectionActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvAlert: TextView

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var yoloDetector: YoloDetector
    
    private var emailSent = false
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)
        tvAlert = findViewById(R.id.tvAlert)

        yoloDetector = YoloDetector(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        processImageProxy(image)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(image: ImageProxy) {
        // CameraX 1.3+ has built-in toBitmap()
        val bitmap = image.toBitmap()
        
        // Rotate bitmap depending on device orientation
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val results = yoloDetector.detect(rotatedBitmap)

        val scaleX = if (rotatedBitmap.width > 0) overlayView.width.toFloat() / rotatedBitmap.width.toFloat() else 1f
        val scaleY = if (rotatedBitmap.height > 0) overlayView.height.toFloat() / rotatedBitmap.height.toFloat() else 1f

        val scaledResults = results.map { box ->
            box.copy(
                x1 = box.x1 * scaleX,
                y1 = box.y1 * scaleY,
                x2 = box.x2 * scaleX,
                y2 = box.y2 * scaleY
            )
        }
        
        var alertTriggered = false
        for (box in scaledResults) {
            if (box.isDanger) {
                alertTriggered = true
                break
            }
        }

        runOnUiThread {
            overlayView.setResults(scaledResults)
            
            if (alertTriggered) {
                tvAlert.visibility = View.VISIBLE
                if (!emailSent) {
                    emailSent = true
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                    saveAndSendEmail(rotatedBitmap)
                }
            } else {
                // tvAlert.visibility = View.GONE // Keep it on if once triggered, or hide if we want
            }
        }

        image.close()
    }

    private fun saveAndSendEmail(bitmap: Bitmap) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cacheDir, "alert_frame_\${timeStamp}.jpg")
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
            
            EmailSender.sendEmailAlert(file.absolutePath, 
                onSuccess = {
                    Toast.makeText(this, "📧 Email sent!", Toast.LENGTH_SHORT).show()
                },
                onError = {
                    Toast.makeText(this, "❌ Failed to send email", Toast.LENGTH_SHORT).show()
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        yoloDetector.close()
        toneGen.release()
    }
}
