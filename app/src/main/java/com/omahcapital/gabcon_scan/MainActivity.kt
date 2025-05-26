package com.omahcapital.gabcon_scan

import okhttp3.*
import java.io.IOException

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var messageTextView: TextView
    private var mediaPlayer: MediaPlayer? = null
    private var scanned = false
    private val client = OkHttpClient()

    // Settings UI
    private lateinit var settingsLayout: LinearLayout
    private lateinit var serverEditText: EditText
    private lateinit var portEditText: EditText
    private lateinit var saveButton: Button

    private var clickCount = 0
    private var lastClickTime = 0L
    private val CLICK_INTERVAL_MS = 1000L // reset click count after 1 second of inactivity
    private val REQUIRED_CLICKS = 5

    private val PREFS_NAME = "ScannerSettings"
    private val PREF_SERVER = "server_ip"
    private val PREF_PORT = "server_port"

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
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
        }

        // Setup click listener for 5 clicks
        previewView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > CLICK_INTERVAL_MS) {
                clickCount = 0 // reset count if too slow
            }
            lastClickTime = currentTime
            clickCount++
            if (clickCount >= REQUIRED_CLICKS) {
                clickCount = 0
                showSettings()
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

        // Root FrameLayout holds preview and message and settings
        val root = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            addView(previewView)
            addView(messageTextView)
        }

        // Create settings layout (initially invisible)
        settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(50, 200, 50, 200)
            }
            setPadding(50, 50, 50, 50)
        }

        // Server IP input
        serverEditText = EditText(this).apply {
            hint = "Server IP"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setHintTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setSingleLine()
        }
        settingsLayout.addView(serverEditText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 20
        })

        // Port input
        portEditText = EditText(this).apply {
            hint = "Port"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setHintTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        settingsLayout.addView(portEditText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 40
        })

        // Save button
        saveButton = Button(this).apply {
            text = "Save"
        }
        settingsLayout.addView(saveButton)

        root.addView(settingsLayout)

        setContentView(root)

        mediaPlayer = MediaPlayer.create(this, R.raw.beep)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun showSettings() {
        runOnUiThread {
            settingsLayout.visibility = View.VISIBLE
            previewView.visibility = View.GONE
            messageTextView.visibility = View.GONE
        }
    }

    private fun hideSettings() {
        runOnUiThread {
            settingsLayout.visibility = View.GONE
            previewView.visibility = View.VISIBLE
            messageTextView.visibility = View.VISIBLE
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val server = prefs.getString(PREF_SERVER, "")
        val port = prefs.getString(PREF_PORT, "")

        serverEditText.setText(server)
        portEditText.setText(port)
    }

    private fun saveSettings() {
        val server = serverEditText.text.toString().trim()
        val port = portEditText.text.toString().trim()

        if (server.isEmpty() || port.isEmpty()) {
            Toast.makeText(this, "Please enter server IP and port", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_SERVER, server)
            putString(PREF_PORT, port)
            apply()
        }

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        hideSettings()
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
        val scannedValue = barcode.rawValue ?: ""

        Log.d("Barcode", "Scanned: $scannedValue")

        // Send to server
        sendBarcodeToServer(scannedValue)

        runOnUiThread {
            messageTextView.text = "Item added"
            mediaPlayer?.start()
        }
        previewView.postDelayed({
            scanned = false
            runOnUiThread { messageTextView.text = "Scan barcode" }
        }, 1000)
    }

    private fun sendBarcodeToServer(barcodeValue: String) {
        val serverIp = serverEditText.text.toString().trim()
        val port = portEditText.text.toString().trim()

        if (serverIp.isEmpty() || port.isEmpty()) {
            Log.e("Network", "Server IP or port not set. Cannot send barcode.")
            return
        }

        // Example: http://<serverIp>:<port>/scan
        val url = "http://$serverIp:$port/itemReceiver"

        // Build JSON payload
        val json = """
            {
                "barcode": "$barcodeValue"
            }
        """.trimIndent()

        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Network", "Failed to send barcode to server", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to send data to server", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("Network", "Server error: ${it.code}")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Server error: ${it.code}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.d("Network", "Barcode sent successfully")
                        // You can add UI updates here if needed
                    }
                }
            }
        })
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
