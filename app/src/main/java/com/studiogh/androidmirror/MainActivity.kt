package com.studiogh.androidmirror

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

class MainActivity : Activity() {
    private val requestCode = 4201
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var server: MirrorServer? = null
    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private val accessToken: String = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString(\"\") { \"%02x\".format(it) }
    private var screenW = 0
    private var screenH = 0
    private lateinit var status: TextView
    private lateinit var address: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screenW = resources.displayMetrics.widthPixels
        screenH = resources.displayMetrics.heightPixels

        status = TextView(this).apply { textSize = 18f; text = "Pronto para iniciar"; setPadding(0, 12, 0, 12) }
        address = TextView(this).apply { textSize = 16f; setPadding(0, 12, 0, 12) }
        val start = Button(this).apply {
            text = "Iniciar espelhamento"
            setOnClickListener {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), requestCode)
            }
        }
        val accessibility = Button(this).apply {
            text = "Ativar controle por toque"
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val stop = Button(this).apply {
            text = "Parar espelhamento"
            setOnClickListener { stopMirror() }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 24)
            addView(TextView(this@MainActivity).apply { text = "ANDROID MIRROR"; textSize = 24f })
            addView(TextView(this@MainActivity).apply {
                text = "Gratuito • Rede Wi-Fi local • Nenhum software no Mac"
                textSize = 14f; setPadding(0, 10, 0, 16)
            })
            addView(status); addView(address); addView(start); addView(accessibility); addView(stop)
            addView(TextView(this@MainActivity).apply {
                text = "Use apenas em uma rede Wi-Fi confiável. Enquanto ativo, qualquer dispositivo da rede que conheça o endereço pode tentar abrir a tela. Desconecte ao terminar."
                textSize = 13f; setPadding(0, 24, 0, 0)
            })
        }
        setContentView(layout)
        address.text = "Endereço aparecerá quando o espelhamento iniciar."
    }

    @Deprecated("Deprecated for compatibility with Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode || resultCode != RESULT_OK || data == null) {
            status.text = "Captura não autorizada."
            return
        }
        stopMirror()
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { runOnUiThread { stopMirror() } }
        }, android.os.Handler(mainLooper))
        startCapture()
        try {
            server = MirrorServer(8765, latestJpeg, screenW, screenH, accessToken)
            server?.start()
            val ip = wifiIp()
            status.text = "Espelhamento ativo. Mantenha este app aberto."
            address.text = if (ip != "0.0.0.0") "No Mac, abra este link privado: http://$ip:8765/?token=$accessToken" else
                "Wi-Fi sem endereço IPv4 detectável. Verifique a conexão e tente novamente."
            if (ip != "0.0.0.0") Toast.makeText(this, "Abra o link privado mostrado no Android Mirror", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            status.text = "Não foi possível iniciar o servidor: ${e.message}"
            stopMirror()
        }
    }

    private fun startCapture() {
        val density = resources.displayMetrics.densityDpi
        reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ ir ->
            val image = try { ir.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * screenW
                val paddedBitmap = Bitmap.createBitmap(
                    screenW + rowPadding / pixelStride, screenH, Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(buffer)
                val bitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, screenW, screenH)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 55, out)
                latestJpeg.set(out.toByteArray())
                bitmap.recycle()
                paddedBitmap.recycle()
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, android.os.Handler(mainLooper))
        virtualDisplay = projection?.createVirtualDisplay(
            "AndroidMirror", screenW, screenH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null
        )
    }

    private fun wifiIp(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        return if (ip == "0.0.0.0") "0.0.0.0" else ip
    }

    private fun stopMirror() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        virtualDisplay?.release(); virtualDisplay = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        latestJpeg.set(null)
        if (::status.isInitialized) status.text = "Espelhamento parado."
        if (::address.isInitialized) address.text = "Inicie novamente para reconectar."
    }

    override fun onDestroy() {
        stopMirror()
        super.onDestroy()
    }
}
