package com.omahcapital.gabcon_scan

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import android.view.Gravity


class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var messageTextView: TextView
    private var mediaPlayer: MediaPlayer? = null
    private var scanned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screenWidth = resources.displayMetrics.widthPixels
        val previewWidth = (screenWidth * 0.9).toInt()
        val previewHeight = (previewWidth / 3) // 3:1 ratio

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                previewWidth,
                previewHeight
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            }
        }

        messageTextView = TextView(this).apply {
            text = "Scan barcode"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 18f
            setPadding(32, 32, 32, 32)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 64 // adjust as needed
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            addView(previewView)
            addView(messageTextView)
        }


        setContentView(root)

        mediaPlayer = MediaPlayer.create(this, R.raw.beep)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val barcodeScanner = BarcodeScanning.getClient()

            val executor = Executors.newSingleThreadExecutor()
            imageAnalysis.setAnalyzer(executor, { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !scanned) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                val barcode = barcodes[0]
                                handleScannedBarcode(barcode)
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            })

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleScannedBarcode(barcode: Barcode) {
        scanned = true
        Log.d("Barcode", "Scanned: ${barcode.rawValue}")
        runOnUiThread {
            messageTextView.text = "Item added"
            mediaPlayer?.start()
        }
        previewView.postDelayed({
            scanned = false
            runOnUiThread { messageTextView.text = "Scan barcode" }
        }, 1000)
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                messageTextView.text = "Camera permission is required."
            }
        }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}
