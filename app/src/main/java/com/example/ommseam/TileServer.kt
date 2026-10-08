package com.example.ommseam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Crops all four PNGs from ONE Canvas image: continuity is true by construction. */
class TileServer(directory: File) : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val tiles = ConcurrentHashMap<String, ByteArray>()
    private val blank: ByteArray
    val port get() = socket.localPort
    fun urlTemplate() = "http://127.0.0.1:$port/tiles/{z}/{x}/{y}.png"

    init {
        val image = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 96f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        // Draw the guide first so it cannot overwrite any glyph pixels.
        paint.color = Color.rgb(40, 100, 220)
        paint.strokeWidth = 1f
        canvas.drawLine(0f, 1024f, 2048f, 1024f, paint)
        paint.color = Color.BLACK
        // Same glyphs and baseline offset, one across y=1024, one inside y=8.
        canvas.drawText("美里町 ABC", 1536f, 1052f, paint)
        canvas.drawText("美里町 ABC", 1536f, 1252f, paint)
        // A narrow ladder also makes missing/repeated scanlines easy to see.
        paint.isAntiAlias = false
        paint.color = Color.BLACK
        for (y in 952..1096 step 8) canvas.drawRect(1880f, y.toFloat(), 1930f, (y + 2).toFloat(), paint)
        File(directory, "source.png").writeBytes(png(image))
        for (x in 0..1) for (y in 0..1) {
            val crop = Bitmap.createBitmap(image, x * 1024, y * 1024, 1024, 1024)
            val bytes = png(crop)
            tiles["4/${7+x}/${7+y}"] = bytes
            File(directory, "4-${7+x}-${7+y}.png").writeBytes(bytes)
            crop.recycle()
        }
        image.recycle()
        val white = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        white.eraseColor(Color.WHITE)
        blank = png(white)
        white.recycle()
    }

    fun start() {
        thread(isDaemon = true, name = "seam-tile-server") {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) { handle(client) }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use {
            it.soTimeout = 5000
            try {
                val reader = it.getInputStream().bufferedReader()
                val path = reader.readLine()?.split(" ")?.getOrNull(1) ?: return
                while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                val key = path.substringAfter("/tiles/").substringBefore(".png")
                val body = tiles[key] ?: blank
                it.getOutputStream().apply {
                    write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
                    write(body)
                    flush()
                }
            } catch (_: java.io.IOException) { /* A pan may cancel the request. */ }
        }
    }
    override fun close() { socket.close() }
    private fun png(bitmap: Bitmap) = ByteArrayOutputStream().also {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
}
