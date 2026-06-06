package com.streamoverlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    companion object {
        const val OVERLAY_PERMISSION_REQUEST = 1001
        const val DEFAULT_URL = "https://tikfinity.zerody.one/widget/myactions?cid=683848&screen=1"
    }

    private lateinit var urlInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var opacitySeekBar: SeekBar
    private lateinit var opacityLabel: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput      = findViewById(R.id.urlInput)
        widthInput    = findViewById(R.id.widthInput)
        heightInput   = findViewById(R.id.heightInput)
        opacitySeekBar = findViewById(R.id.opacitySeekBar)
        opacityLabel  = findViewById(R.id.opacityLabel)
        startButton   = findViewById(R.id.startButton)
        stopButton    = findViewById(R.id.stopButton)
        statusText    = findViewById(R.id.statusText)

        urlInput.setText(DEFAULT_URL)
        widthInput.setText("600")
        heightInput.setText("400")
        opacitySeekBar.progress = 90

        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityLabel.text = "Opacité : $progress%"
                // Mettre à jour l'overlay en direct si actif
                val intent = Intent(this@MainActivity, OverlayService::class.java)
                intent.action = OverlayService.ACTION_UPDATE_OPACITY
                intent.putExtra("opacity", progress)
                startService(intent)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        startButton.setOnClickListener { checkPermissionAndStart() }
        stopButton.setOnClickListener  { stopOverlay() }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val running = OverlayService.isRunning
        startButton.isEnabled = !running
        stopButton.isEnabled  = running
        statusText.text = if (running) "✅ Overlay actif" else "⏹ Overlay arrêté"
        statusText.setTextColor(if (running) 0xFF2E7D32.toInt() else 0xFF757575.toInt())
    }

    private fun checkPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                "Autorise l'affichage par-dessus les autres apps",
                Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        } else {
            startOverlay()
        }
    }

    private fun startOverlay() {
        val url     = urlInput.text.toString().trim()
        val width   = widthInput.text.toString().toIntOrNull() ?: 600
        val height  = heightInput.text.toString().toIntOrNull() ?: 400
        val opacity = opacitySeekBar.progress

        if (url.isEmpty()) {
            Toast.makeText(this, "Entrez une URL", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
            putExtra("url",     url)
            putExtra("width",   width)
            putExtra("height",  height)
            putExtra("opacity", opacity)
        }
        startService(intent)
        moveTaskToBack(true) // Passer l'app en arrière-plan
        updateUI()
    }

    private fun stopOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        updateUI()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startOverlay()
            } else {
                Toast.makeText(this,
                    "Permission refusée. Impossible d'afficher l'overlay.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}
