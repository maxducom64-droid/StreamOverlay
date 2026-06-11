package com.streamoverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.webkit.*
import android.widget.Button
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
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

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
            ACTION_STOP           -> stopOverlay()
            ACTION_UPDATE_OPACITY -> {
                val opacity = intent.getIntExtra("opacity", 90)
                overlayView?.alpha = opacity / 100f
            }
        }
        return START_STICKY
    }

    private fun startOverlay(url: String, width: Int, height: Int, opacity: Int) {
        stopOverlay()

        // Démarrer en foreground selon la version Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        val inflater = LayoutInflater.from(this)
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
            x = 10; y = 150
        }

        setupDrag()
        windowManager.addView(overlayView, params)
        isRunning = true
    }

    private fun setupWebView(url: String) {
        webView = overlayView!!.findViewById(R.id.webView)
        webView!!.apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(
                        "document.body.style.background='transparent';" +
                        "document.documentElement.style.background='transparent';",
                        null
                    )
                }
            }
            loadUrl(url)
        }
    }

    private fun setupControls() {
        overlayView!!.findViewById<Button>(R.id.btnClose).setOnClickListener {
            stopOverlay()
        }
        overlayView!!.findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        overlayView!!.findViewById<Button>(R.id.btnReload).setOnClickListener {
            webView?.reload()
        }
    }

    private fun setupDrag() {
        overlayView!!.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x; initialY = params!!.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun stopOverlay() {
        overlayView?.let { windowManager.removeView(it); overlayView = null }
        webView?.destroy(); webView = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Stream Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Overlay actif" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Stream Overlay actif")
            .setContentText("Tap pour ouvrir")
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Arrêter", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }
}
