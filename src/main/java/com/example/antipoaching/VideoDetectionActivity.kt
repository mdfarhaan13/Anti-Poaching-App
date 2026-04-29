package com.example.antipoaching

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoDetectionActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private lateinit var textureView: TextureView
    private lateinit var overlayView: OverlayView
    private lateinit var tvAlert: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var yoloDetector: YoloDetector
    private var mediaPlayer: MediaPlayer? = null
    private var detectionJob: Job? = null
    
    private var videoWidth = 0
    private var videoHeight = 0
    private var emailSent = false
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        textureView = findViewById(R.id.textureView)
        overlayView = findViewById(R.id.overlayView)
        tvAlert = findViewById(R.id.tvAlert)
        progressBar = findViewById(R.id.progressBar)

        textureView.surfaceTextureListener = this
        yoloDetector = YoloDetector(this)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val videoUriStr = intent.getStringExtra("videoUri")
        if (videoUriStr != null) {
            startVideo(Uri.parse(videoUriStr), Surface(surface))
        }
    }

    private fun startVideo(uri: Uri, surface: Surface) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@VideoDetectionActivity, uri)
            setSurface(surface)
            prepareAsync()
            setOnPreparedListener { mp ->
                this@VideoDetectionActivity.videoWidth = mp.videoWidth
                this@VideoDetectionActivity.videoHeight = mp.videoHeight
                adjustAspectRatio(textureView.width, textureView.height)
                mp.start()
                startDetectionLoop()
            }
            setOnCompletionListener {
                stopDetectionLoop()
                Toast.makeText(this@VideoDetectionActivity, "✅ Detection completed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun adjustAspectRatio(viewWidth: Int, viewHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0) return
        val matrix = Matrix()
        val viewRatio = viewWidth.toFloat() / viewHeight
        val videoRatio = videoWidth.toFloat() / videoHeight
        var scaleX = 1f
        var scaleY = 1f
        if (viewRatio > videoRatio) {
            scaleX = videoRatio / viewRatio
        } else {
            scaleY = viewRatio / videoRatio
        }
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        textureView.setTransform(matrix)
    }

    private fun startDetectionLoop() {
        detectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                // Capture current frame from TextureView
                // Using 640x640 for detector efficiency
                val bitmap = withContext(Dispatchers.Main) {
                    textureView.getBitmap(640, 640)
                }
                
                if (bitmap != null) {
                    val results = yoloDetector.detect(bitmap)
                    
                    // Map results back to screen coordinates
                    // Since TextureView is transformed, we need to account for that.
                    // However, OverlayView covers the WHOLE TextureView.
                    // The detected bitmap is a 640x640 version of what's VISIBLE on the TextureView.
                    
                    val scaleX = textureView.width.toFloat() / 640f
                    val scaleY = textureView.height.toFloat() / 640f
                    
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

                    withContext(Dispatchers.Main) {
                        overlayView.setResults(scaledResults)
                        if (alertTriggered) {
                            tvAlert.visibility = View.VISIBLE
                            if (!emailSent) {
                                emailSent = true
                                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                                saveAndSendEmail(bitmap)
                            }
                        }
                    }
                }
                delay(200) // Detect 5 times per second
            }
        }
    }

    private fun stopDetectionLoop() {
        detectionJob?.cancel()
    }

    private fun saveAndSendEmail(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(cacheDir, "alert_frame_${timeStamp}.jpg")
            try {
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                out.close()
                
                EmailSender.sendEmailAlert(file.absolutePath, 
                    onSuccess = {
                        Toast.makeText(this@VideoDetectionActivity, "📧 Email sent!", Toast.LENGTH_SHORT).show()
                    },
                    onError = {
                        Toast.makeText(this@VideoDetectionActivity, "❌ Failed to send email", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        adjustAspectRatio(width, height)
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onDestroy() {
        super.onDestroy()
        stopDetectionLoop()
        mediaPlayer?.release()
        yoloDetector.close()
        toneGen.release()
    }
}
