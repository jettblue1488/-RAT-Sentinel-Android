package com.kay.ratsentinel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class ScreenShareService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_TOKEN = "screen_share_token"
        const val ACTION_STOP = "com.kay.ratsentinel.STOP_SCREEN_SHARE"
        const val PORT = 8765
    }

    @Volatile private var latestFrame: ByteArray? = null
    @Volatile private var token: String = ""
    private val running = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var server: ServerSocket? = null
    private var serverThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(42, makeNotification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtMost(1280)
            val height = (width * metrics.heightPixels / metrics.widthPixels).coerceAtLeast(1)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader?.setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val paddedWidth = rowStride / pixelStride
                        val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        val out = java.io.ByteArrayOutputStream()
                        cropped.compress(Bitmap.CompressFormat.JPEG, 55, out)
                        latestFrame = out.toByteArray()
                        bitmap.recycle()
                        if (cropped !== bitmap) cropped.recycle()
                        out.close()
                    } catch (_: Exception) {
                    } finally { image.close() }
                }
            }, null)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, null)
            virtualDisplay = projection?.createVirtualDisplay(
                "RAT Sentinel supervised screen share", width, height,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface, null, null
            )
            token = intent.getStringExtra(EXTRA_TOKEN) ?: run { stopSelf(); return START_NOT_STICKY }
            startServer()
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        running.set(true)
        serverThread = Thread {
            try {
                server = ServerSocket(PORT)
                while (running.get()) {
                    val client = server?.accept() ?: break
                    Thread { serve(client) }.start()
                }
            } catch (_: Exception) {
                stopSelf()
            }
        }.apply { name = "RAT-Screen-HTTP"; start() }
    }

    private fun serve(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 5000
                val input = client.getInputStream()
                val request = ByteArray(2048)
                val count = input.read(request)
                val line = if (count > 0) String(request, 0, count).lineSequence().firstOrNull().orEmpty() else ""
                if (!line.contains("/$token")) {
                    client.getOutputStream().apply {
                        write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                    return
                }
                val out = BufferedOutputStream(client.getOutputStream())
                out.write(("HTTP/1.1 200 OK\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n").toByteArray())
                out.flush()
                while (running.get() && !client.isClosed) {
                    val frame = latestFrame
                    if (frame != null) {
                        out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
                        out.write(frame)
                        out.write("\r\n".toByteArray())
                        out.flush()
                    }
                    Thread.sleep(120)
                }
            } catch (_: Exception) { }
        }
    }

    private fun makeNotification(): Notification {
        val channelId = "screen_share"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Screen sharing", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val stop = Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP)
        val pending = android.app.PendingIntent.getService(this, 43, stop,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("RAT Sentinel screen sharing is active")
            .setContentText("Your screen is being shared on the local Wi-Fi network. Tap Stop to end.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop sharing", pending)
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        try { server?.close() } catch (_: Exception) { }
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        latestFrame = null
        super.onDestroy()
    }
}
