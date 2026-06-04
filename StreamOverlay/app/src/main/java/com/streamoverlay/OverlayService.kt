package com.streamoverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.webkit.*
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val ACTION_START          = "ACTION_START"
        const val ACTION_STOP           = "ACTION_STOP"
        const val ACTION_UPDATE_OPACITY = "ACTION_UPDATE_OPACITY"
        const val CHANNEL_ID            = "StreamOverlayChannel"
        const val NOTIF_ID              = 1
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var webView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    // Variables pour le déplacement par drag
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url     = intent.getStringExtra("url")     ?: return START_NOT_STICKY
                val width   = intent.getIntExtra("width",   600)
                val height  = intent.getIntExtra("height",  400)
                val opacity = intent.getIntExtra("opacity", 90)
                startOverlay(url, width, height, opacity)
            }
            ACTION_STOP -> stopOverlay()
            ACTION_UPDATE_OPACITY -> {
                val opacity = intent.getIntExtra("opacity", 90)
                overlayView?.alpha = opacity / 100f
            }
        }
        return START_STICKY
    }

    private fun startOverlay(url: String, width: Int, height: Int, opacity: Int) {
        stopOverlay() // Arrêter l'overlay existant si présent

        startForeground(NOTIF_ID, buildNotification())

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)
        overlayView!!.alpha = opacity / 100f

        setupWebView(url)
        setupControls()

        params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 10
            y = 150
        }

        setupDragAndResize()

        windowManager.addView(overlayView, params)
        isRunning = true
    }

    private fun setupWebView(url: String) {
        webView = overlayView!!.findViewById(R.id.webView)
        webView!!.apply {
            setBackgroundColor(0x00000000) // Transparent
            settings.apply {
                javaScriptEnabled          = true
                domStorageEnabled          = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode           = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode                  = WebSettings.LOAD_NO_CACHE
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Injecter du CSS pour rendre le fond transparent
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var style = document.createElement('style');
                            style.innerHTML = 'html, body { background: transparent !important; background-color: transparent !important; }';
                            document.head.appendChild(style);
                        })();
                        """.trimIndent(), null
                    )
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(url)
        }
    }

    private fun setupControls() {
        // Bouton fermer
        overlayView!!.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            stopOverlay()
        }

        // Bouton retour app
        overlayView!!.findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        // Bouton recharger
        overlayView!!.findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            webView?.reload()
        }
    }

    private fun setupDragAndResize() {
        val dragHandle = overlayView!!.findViewById<View>(R.id.dragHandle)

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX      = params!!.x
                    initialY      = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging    = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    if (isDragging) {
                        params!!.x = initialX + dx.toInt()
                        params!!.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(overlayView, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun stopOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        webView?.destroy()
        webView = null
        isRunning = false
        stopForeground(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stream Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Overlay Tikfinity actif" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Stream Overlay actif")
            .setContentText("Tikfinity overlay en cours")
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "Arrêter", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }
}
