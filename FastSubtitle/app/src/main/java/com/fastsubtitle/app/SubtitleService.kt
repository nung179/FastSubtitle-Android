package com.fastsubtitle.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SubtitleService : Service() {

    companion object {

        const val EXTRA_RESULT_CODE =
            "result_code"

        const val EXTRA_RESULT_DATA =
            "result_data"

        const val EXTRA_API_KEY =
            "api_key"

        const val AKSI_BERHENTI =
            "berhenti"

        const val ID_CHANNEL =
            "fastsubtitle"

        const val ID_NOTIFIKASI =
            1001
    }

    private var aktif =
        false

    private var mediaProjection:
        MediaProjection? =
        null

    private var audioRecord:
        AudioRecord? =
        null

    private var socket:
        WebSocket? =
        null

    private var windowManager:
        WindowManager? =
        null

    private var overlay:
        TextView? =
        null

    private val client =
        OkHttpClient.Builder()
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .build()

    override fun onBind(
        intent: Intent?
    ): IBinder? =
        null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action ==
            AKSI_BERHENTI
        ) {

            berhenti()

            return START_NOT_STICKY
        }

        buatChannel()

        val notifikasi =
            NotificationCompat.Builder(
                this,
                ID_CHANNEL
            )
                .setContentTitle(
                    "Fast Subtitle aktif"
                )
                .setContentText(
                    "Menangkap audio internal..."
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(
                    true
                )
                .build()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            startForeground(
                ID_NOTIFIKASI,
                notifikasi,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                ID_NOTIFIKASI,
                notifikasi
            )
        }

        val apiKey =
            intent
                ?.getStringExtra(
                    EXTRA_API_KEY
                )
                .orEmpty()

        val resultCode =
            intent
                ?.getIntExtra(
                    EXTRA_RESULT_CODE,
                    0
                )
                ?: 0

        @Suppress("DEPRECATION")
        val resultData =
            intent?.getParcelableExtra(
                EXTRA_RESULT_DATA
            ) as? Intent

        if (
            resultData == null ||
            apiKey.isBlank()
        ) {

            berhenti()

            return START_NOT_STICKY
        }

        try {

            val manager =
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            mediaProjection =
                manager.getMediaProjection(
                    resultCode,
                    resultData
                )

            buatOverlay()

            bukaDeepgram(
                apiKey
            )

        } catch (
            e: Exception
        ) {

            tampilkan(
                "Gagal memulai: ${e.message}"
            )

            berhentiTertunda()
        }

        return START_NOT_STICKY
    }

    private fun bukaDeepgram(
        apiKey: String
    ) {

        val request =
            Request.Builder()
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
                    "Token $apiKey"
                )
                .build()

        socket =
            client.newWebSocket(
                request,
                object :
                    WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        mulaiAudioInternal()
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        try {

                            val json =
                                JSONObject(
                                    text
                                )

                            val hasil =
                                json
                                    .optJSONObject(
                                        "channel"
                                    )
                                    ?.optJSONArray(
                                        "alternatives"
                                    )
                                    ?.optJSONObject(
                                        0
                                    )
                                    ?.optString(
                                        "transcript"
                                    )
                                    .orEmpty()

                            if (
                                hasil.isNotBlank()
                            ) {

                                tampilkan(
                                    hasil
                                )
                            }

                        } catch (
                            _: Exception
                        ) {
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        tampilkan(
                            "Koneksi gagal: ${t.message}"
                        )

                        berhentiTertunda()
                    }
                }
            )
    }

    private fun mulaiAudioInternal() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {

            tampilkan(
                "Android 10+ diperlukan."
            )

            return
        }

        val projection =
            mediaProjection
                ?: return

        try {

            val konfigurasiCapture =
                AudioPlaybackCaptureConfiguration
                    .Builder(
                        projection
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_MEDIA
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_GAME
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_UNKNOWN
                    )
                    .build()

            val format =
                AudioFormat.Builder()
                    .setEncoding(
                        AudioFormat
                            .ENCODING_PCM_16BIT
                    )
                    .setSampleRate(
                        16000
                    )
                    .setChannelMask(
                        AudioFormat
                            .CHANNEL_IN_MONO
                    )
                    .build()

            val ukuranMinimal =
                AudioRecord
                    .getMinBufferSize(
                        16000,
                        AudioFormat
                            .CHANNEL_IN_MONO,
                        AudioFormat
                            .ENCODING_PCM_16BIT
                    )

            val ukuranBuffer =
                if (
                    ukuranMinimal > 0
                ) {

                    ukuranMinimal * 2

                } else {

                    8192
                }

            audioRecord =
                AudioRecord
                    .Builder()
                    .setAudioFormat(
                        format
                    )
                    .setBufferSizeInBytes(
                        ukuranBuffer
                    )
                    .setAudioPlaybackCaptureConfig(
                        konfigurasiCapture
                    )
                    .build()

            audioRecord
                ?.startRecording()

            aktif =
                true

            tampilkan(
                "Mendengarkan audio..."
            )

            Thread {

                val buffer =
                    ByteArray(
                        ukuranBuffer
                    )

                while (
                    aktif
                ) {

                    val jumlah =
                        audioRecord
                            ?.read(
                                buffer,
                                0,
                                buffer.size
                            )
                            ?: 0

                    if (
                        jumlah > 0
                    ) {

                        socket
                            ?.send(
                                ByteString.of(
                                    *buffer.copyOf(
                                        jumlah
                                    )
                                )
                            )
                    }
                }

            }.start()

        } catch (
            e: Exception
        ) {

            tampilkan(
                "Audio internal gagal: ${e.message}"
            )

            berhentiTertunda()
        }
    }

    private fun buatOverlay() {

        if (
            overlay != null
        ) {
            return
        }

        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        overlay =
            TextView(
                this
            ).apply {

                text =
                    "Fast Subtitle aktif"

                textSize =
                    23f

                // ==========================
                // WARNA SUBTITLE
                // ==========================
                setTextColor(
                    Color.YELLOW
                )

                // ==========================
                // SHADOW HITAM
                // ==========================
                setShadowLayer(
                    6f,
                    2f,
                    2f,
                    Color.BLACK
                )

                // ==========================
                // LATAR TRANSPARAN
                // ==========================
                setBackgroundColor(
                    Color.TRANSPARENT
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    30,
                    16,
                    30,
                    16
                )

                maxLines =
                    3
            }

        val jenis =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY

            } else {

                @Suppress("DEPRECATION")
                WindowManager.LayoutParams
                    .TYPE_PHONE
            }

        val parameter =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                jenis,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.BOTTOM or
                        Gravity.CENTER_HORIZONTAL

                y =
                    140
            }

        windowManager
            ?.addView(
                overlay,
                parameter
            )
    }

    private fun tampilkan(
        teks: String
    ) {

        overlay
            ?.post {

                overlay
                    ?.text =
                    teks
            }
    }

    private fun hapusOverlay() {

        try {

            overlay
                ?.let {

                    windowManager
                        ?.removeView(
                            it
                        )
                }

        } catch (
            _: Exception
        ) {
        }

        overlay =
            null
    }

    private fun berhentiTertunda() {

        android.os.Handler(
            mainLooper
        ).postDelayed(
            {
                berhenti()
            },
            2500
        )
    }

    private fun berhenti() {

        aktif =
            false

        try {

            audioRecord
                ?.stop()

        } catch (
            _: Exception
        ) {
        }

        try {

            audioRecord
                ?.release()

        } catch (
            _: Exception
        ) {
        }

        audioRecord =
            null

        try {

            socket
                ?.close(
                    1000,
                    "selesai"
                )

        } catch (
            _: Exception
        ) {
        }

        socket =
            null

        try {

            mediaProjection
                ?.stop()

        } catch (
            _: Exception
        ) {
        }

        mediaProjection =
            null

        hapusOverlay()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {

        aktif =
            false

        hapusOverlay()

        super.onDestroy()
    }

    private fun buatChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    ID_CHANNEL,
                    "Fast Subtitle",
                    NotificationManager
                        .IMPORTANCE_LOW
                )

            channel.description =
                "Status Fast Subtitle"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager
                .createNotificationChannel(
                    channel
                )
        }
    }
}
