package com.fastsubtitle.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class SubtitleService : Service() {

    companion object {

        const val EXTRA_RESULT_CODE =
            "result_code"

        const val EXTRA_RESULT_DATA =
            "result_data"

        const val EXTRA_API_KEY =
            "api_key"

        const val EXTRA_BAHASA_SUMBER =
            "bahasa_sumber"

        const val EXTRA_BAHASA_TUJUAN =
            "bahasa_tujuan"

        const val EXTRA_WARNA =
            "warna"

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

    private var parameterOverlay:
        WindowManager.LayoutParams? =
        null

    private var translator:
        Translator? =
        null

    private var translatorSiap =
        false

    private var bahasaSumber =
        "zh"

    private var bahasaTujuan =
        "id"

    private var warna =
        "kuning"

    private var urutanTerjemahan =
        0L

    private var jumlahAudio =
        0L

    private var audioBersuara =
        false

    private val client =
        OkHttpClient.Builder()
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .pingInterval(
                8,
                TimeUnit.SECONDS
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
                    "Menangkap audio internal"
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

        bahasaSumber =
            intent
                ?.getStringExtra(
                    EXTRA_BAHASA_SUMBER
                )
                ?: "zh"

        bahasaTujuan =
            intent
                ?.getStringExtra(
                    EXTRA_BAHASA_TUJUAN
                )
                ?: "id"

        warna =
            intent
                ?.getStringExtra(
                    EXTRA_WARNA
                )
                ?: "kuning"

        val resultCode =
            intent
                ?.getIntExtra(
                    EXTRA_RESULT_CODE,
                    0
                )
                ?: 0

        @Suppress("DEPRECATION")
        val resultData =
            intent
                ?.getParcelableExtra(
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

            siapkanTranslator()

            bukaDeepgram(
                apiKey
            )

        } catch (
            e: Exception
        ) {

            tampilkan(
                "Gagal memulai:\n${e.message}"
            )

            berhentiTertunda()
        }

        return START_NOT_STICKY
    }

    private fun siapkanTranslator() {

        val sumber =
            TranslateLanguage
                .fromLanguageTag(
                    bahasaSumber
                )

        val tujuan =
            TranslateLanguage
                .fromLanguageTag(
                    bahasaTujuan
                )

        if (
            sumber == null ||
            tujuan == null
        ) {

            tampilkan(
                "Bahasa terjemahan tidak didukung."
            )

            return
        }

        val opsi =
            TranslatorOptions.Builder()
                .setSourceLanguage(
                    sumber
                )
                .setTargetLanguage(
                    tujuan
                )
                .build()

        translator =
            Translation.getClient(
                opsi
            )

        translatorSiap =
            false

        val kondisi =
            DownloadConditions.Builder()
                .build()

        tampilkan(
            "Menyiapkan terjemahan..."
        )

        translator
            ?.downloadModelIfNeeded(
                kondisi
            )
            ?.addOnSuccessListener {

                translatorSiap =
                    true

                tampilkan(
                    "Siap. Putar video..."
                )
            }
            ?.addOnFailureListener {

                tampilkan(
                    "Model terjemahan gagal diunduh."
                )
            }
    }

    private fun bukaDeepgram(
        apiKey: String
    ) {

        val alamat =
            "wss://api.deepgram.com/v1/listen" +
                "?model=nova-3" +
                "&language=$bahasaSumber" +
                "&interim_results=true" +
                "&smart_format=true" +
                "&endpointing=150" +
                "&encoding=linear16" +
                "&sample_rate=48000" +
                "&channels=2"

        val request =
            Request.Builder()
                .url(
                    alamat
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

                                terjemahkan(
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
                            "Deepgram gagal:\n${t.message}"
                        )

                        berhentiTertunda()
                    }
                }
            )
    }

    private fun terjemahkan(
        teks: String
    ) {

        val mesin =
            translator

        if (
            mesin == null ||
            !translatorSiap
        ) {

            tampilkan(
                teks
            )

            return
        }

        urutanTerjemahan +=
            1

        val nomor =
            urutanTerjemahan

        mesin
            .translate(
                teks
            )
            .addOnSuccessListener {
                    hasil ->

                if (
                    nomor ==
                    urutanTerjemahan &&
                    hasil.isNotBlank()
                ) {

                    tampilkan(
                        hasil
                    )
                }
            }
            .addOnFailureListener {

                tampilkan(
                    teks
                )
            }
    }

    private fun mulaiAudioInternal() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {

            tampilkan(
                "Minimal Android 10."
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
                        48000
                    )
                    .setChannelMask(
                        AudioFormat
                            .CHANNEL_IN_STEREO
                    )
                    .build()

            val ukuranMinimal =
                AudioRecord
                    .getMinBufferSize(
                        48000,
                        AudioFormat
                            .CHANNEL_IN_STEREO,
                        AudioFormat
                            .ENCODING_PCM_16BIT
                    )

            val ukuranBuffer =
                if (
                    ukuranMinimal > 0
                ) {

                    ukuranMinimal * 2

                } else {

                    16384
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

            if (
                audioRecord
                    ?.state !=
                AudioRecord.STATE_INITIALIZED
            ) {

                tampilkan(
                    "Audio internal tidak dapat dibuka."
                )

                return
            }

            audioRecord
                ?.startRecording()

            aktif =
                true

            jumlahAudio =
                0

            audioBersuara =
                false

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

                        jumlahAudio +=
                            jumlah

                        cekAudio(
                            buffer,
                            jumlah
                        )

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

            android.os.Handler(
                mainLooper
            ).postDelayed(
                {

                    if (
                        aktif &&
                        !audioBersuara
                    ) {

                        tampilkan(
                            "Audio tidak tertangkap.\n" +
                                "Pastikan video sedang berbunyi."
                        )
                    }

                },
                5000
            )

        } catch (
            e: Exception
        ) {

            tampilkan(
                "Audio internal gagal:\n${e.message}"
            )

            berhentiTertunda()
        }
    }

    private fun cekAudio(
        data: ByteArray,
        jumlah: Int
    ) {

        var i =
            0

        while (
            i + 1 <
            jumlah
        ) {

            val sample =
                (
                    (data[i + 1]
                        .toInt() shl 8) or
                        (
                            data[i]
                                .toInt() and 0xFF
                            )
                    ).toShort()
                    .toInt()

            if (
                abs(
                    sample
                ) > 200
            ) {

                audioBersuara =
                    true

                return
            }

            i +=
                2
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

        val latar =
            GradientDrawable().apply {

                setColor(
                    Color.argb(
                        170,
                        0,
                        0,
                        0
                    )
                )

                cornerRadius =
                    22f
            }

        overlay =
            TextView(
                this
            ).apply {

                text =
                    "Fast Subtitle aktif"

                textSize =
                    22f

                setTextColor(
                    if (
                        warna ==
                        "putih"
                    ) {
                        Color.WHITE
                    } else {
                        Color.YELLOW
                    }
                )

                setShadowLayer(
                    5f,
                    2f,
                    2f,
                    Color.BLACK
                )

                background =
                    latar

                gravity =
                    Gravity.CENTER

                setPadding(
                    28,
                    14,
                    28,
                    14
                )

                maxLines =
                    3
            }

        val jenis =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                WindowManager
                    .LayoutParams
                    .TYPE_APPLICATION_OVERLAY

            } else {

                @Suppress("DEPRECATION")
                WindowManager
                    .LayoutParams
                    .TYPE_PHONE
            }

        val preferensi =
            getSharedPreferences(
                "fastsubtitle_overlay",
                MODE_PRIVATE
            )

        parameterOverlay =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                jenis,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.TOP or
                        Gravity.START

                x =
                    preferensi.getInt(
                        "x",
                        40
                    )

                y =
                    preferensi.getInt(
                        "y",
                        1000
                    )
            }

        overlay
            ?.setOnTouchListener(
                object :
                    android.view.View.OnTouchListener {

                    private var awalX =
                        0

                    private var awalY =
                        0

                    private var sentuhX =
                        0f

                    private var sentuhY =
                        0f

                    override fun onTouch(
                        view: android.view.View?,
                        event: MotionEvent
                    ): Boolean {

                        val parameter =
                            parameterOverlay
                                ?: return false

                        when (
                            event.action
                        ) {

                            MotionEvent.ACTION_DOWN -> {

                                awalX =
                                    parameter.x

                                awalY =
                                    parameter.y

                                sentuhX =
                                    event.rawX

                                sentuhY =
                                    event.rawY

                                return true
                            }

                            MotionEvent.ACTION_MOVE -> {

                                parameter.x =
                                    awalX +
                                        (
                                            event.rawX -
                                                sentuhX
                                            ).toInt()

                                parameter.y =
                                    awalY +
                                        (
                                            event.rawY -
                                                sentuhY
                                            ).toInt()

                                try {

                                    windowManager
                                        ?.updateViewLayout(
                                            overlay,
                                            parameter
                                        )

                                } catch (
                                    _: Exception
                                ) {
                                }

                                return true
                            }

                            MotionEvent.ACTION_UP -> {

                                preferensi.edit()
                                    .putInt(
                                        "x",
                                        parameter.x
                                    )
                                    .putInt(
                                        "y",
                                        parameter.y
                                    )
                                    .apply()

                                return true
                            }
                        }

                        return false
                    }
                }
            )

        windowManager
            ?.addView(
                overlay,
                parameterOverlay
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
            3000
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

            translator
                ?.close()

        } catch (
            _: Exception
        ) {
        }

        translator =
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

        try {

            translator
                ?.close()

        } catch (
            _: Exception
        ) {
        }

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
