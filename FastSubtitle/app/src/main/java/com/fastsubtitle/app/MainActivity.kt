package com.fastsubtitle.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var apiKey: EditText
    private lateinit var subtitle: TextView
    private lateinit var tombol: Button

    private var aktif = false
    private var rekam: AudioRecord? = null
    private var socket: WebSocket? = null

    private var windowManager: WindowManager? = null
    private var overlaySubtitle: TextView? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(
            TextView(this).apply {
                text = "Fast Subtitle"
                textSize = 30f
                setTextColor(Color.WHITE)
            }
        )

        root.addView(
            TextView(this).apply {
                text = "Speech-to-Text Online"
                textSize = 16f
                setTextColor(Color.LTGRAY)
            }
        )

        apiKey = EditText(this).apply {
            hint = "Tempel API Key Deepgram"
            setSingleLine(true)
        }

        root.addView(
            apiKey,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 36
            }
        )

        tombol = Button(this).apply {
            text = "MULAI"
            setOnClickListener {
                if (aktif) {
                    berhenti()
                } else {
                    mulai()
                }
            }
        }

        root.addView(
            tombol,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
            }
        )

        subtitle = TextView(this).apply {
            text = "Subtitle akan tampil di sini..."
            textSize = 25f
            gravity = Gravity.CENTER
            setPadding(20, 40, 20, 40)
            setTextColor(Color.WHITE)
        }

        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 20
            }
        )

        setContentView(root)
    }

    private fun mulai() {

        if (apiKey.text.isBlank()) {
            Toast.makeText(
                this,
                "Masukkan API Key Deepgram",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {

            Toast.makeText(
                this,
                "Izinkan Fast Subtitle tampil di atas aplikasi lain",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )

            startActivity(intent)
            return
        }

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                10
            )

            return
        }

        buatOverlay()

        val request = Request.Builder()
            .url(
                "wss://api.deepgram.com/v1/listen" +
                    "?model=nova-3" +
                    "&language=id" +
                    "&interim_results=true" +
                    "&smart_format=true" +
                    "&encoding=linear16" +
                    "&sample_rate=16000" +
                    "&channels=1"
            )
            .addHeader(
                "Authorization",
                "Token ${apiKey.text}"
            )
            .build()

        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    runOnUiThread {
                        mulaiRekam()
                    }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {

                    try {

                        val json = JSONObject(text)

                        val hasil =
                            json.optJSONObject("channel")
                                ?.optJSONArray("alternatives")
                                ?.optJSONObject(0)
                                ?.optString("transcript")
                                .orEmpty()

                        if (hasil.isNotBlank()) {

                            runOnUiThread {

                                subtitle.text = hasil

                                overlaySubtitle?.text = hasil
                            }
                        }

                    } catch (_: Exception) {
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    throwable: Throwable,
                    response: Response?
                ) {

                    runOnUiThread {

                        val pesan =
                            "Koneksi gagal: ${throwable.message}"

                        subtitle.text = pesan

                        overlaySubtitle?.text = pesan

                        berhenti()
                    }
                }
            }
        )

        aktif = true

        tombol.text = "BERHENTI"

        subtitle.text = "Menghubungkan..."

        overlaySubtitle?.text = "Menghubungkan..."
    }

    private fun mulaiRekam() {

        val ukuranBuffer =
            AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        rekam = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            ukuranBuffer * 2
        )

        rekam?.startRecording()

        subtitle.text = "Mendengarkan..."

        overlaySubtitle?.text = "Mendengarkan..."

        Thread {

            val buffer = ByteArray(ukuranBuffer)

            while (aktif) {

                val jumlah =
                    rekam?.read(
                        buffer,
                        0,
                        buffer.size
                    ) ?: 0

                if (jumlah > 0) {

                    socket?.send(
                        ByteString.of(
                            *buffer.copyOf(jumlah)
                        )
                    )
                }
            }

        }.start()
    }

    private fun buatOverlay() {

        if (overlaySubtitle != null) {
            return
        }

        windowManager =
            getSystemService(WINDOW_SERVICE)
                    as WindowManager

        overlaySubtitle =
            TextView(this).apply {

                text = "Fast Subtitle aktif"

                textSize = 24f

                setTextColor(Color.WHITE)

                setBackgroundColor(
                    Color.argb(
                        180,
                        0,
                        0,
                        0
                    )
                )

                gravity = Gravity.CENTER

                setPadding(
                    28,
                    16,
                    28,
                    16
                )
            }

        val jenisWindow =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val parameter =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                jenisWindow,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.BOTTOM or
                        Gravity.CENTER_HORIZONTAL

                y = 120
            }

        windowManager?.addView(
            overlaySubtitle,
            parameter
        )
    }

    private fun hapusOverlay() {

        try {

            if (overlaySubtitle != null) {

                windowManager?.removeView(
                    overlaySubtitle
                )
            }

        } catch (_: Exception) {
        }

        overlaySubtitle = null
    }

    private fun berhenti() {

        aktif = false

        try {

            rekam?.stop()

            rekam?.release()

        } catch (_: Exception) {
        }

        rekam = null

        socket?.close(
            1000,
            "selesai"
        )

        socket = null

        tombol.text = "MULAI"

        subtitle.text = "Subtitle dihentikan"

        hapusOverlay()
    }

    override fun onDestroy() {

        berhenti()

        super.onDestroy()
    }
}
