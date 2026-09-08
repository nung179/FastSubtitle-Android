package com.fastsubtitle.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import okhttp3.*
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
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,48,32,32); gravity=Gravity.CENTER_HORIZONTAL }
        root.addView(TextView(this).apply { text="Fast Subtitle"; textSize=30f; setTextColor(Color.WHITE) })
        root.addView(TextView(this).apply { text="Speech-to-Text Online"; textSize=16f; setTextColor(Color.LTGRAY) })
        apiKey = EditText(this).apply { hint="Tempel API Key Deepgram"; setSingleLine(true) }
        root.addView(apiKey, LinearLayout.LayoutParams(-1,-2).apply { topMargin=36 })
        tombol = Button(this).apply { text="MULAI"; setOnClickListener { if (aktif) berhenti() else mulai() } }
        root.addView(tombol, LinearLayout.LayoutParams(-1,-2).apply { topMargin=20 })
        subtitle = TextView(this).apply { text="Subtitle akan tampil di sini…"; textSize=25f; gravity=Gravity.CENTER; setPadding(20,40,20,40); setTextColor(Color.WHITE) }
        root.addView(subtitle, LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=20 })
        setContentView(root)
    }

    private fun mulai() {
        if (apiKey.text.isBlank()) { Toast.makeText(this,"Masukkan API Key Deepgram",Toast.LENGTH_SHORT).show(); return }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10); return
        }
        val req = Request.Builder()
            .url("wss://api.deepgram.com/v1/listen?model=nova-3&language=id&interim_results=true&smart_format=true&encoding=linear16&sample_rate=16000&channels=1")
            .addHeader("Authorization", "Token ${apiKey.text}").build()
        socket = client.newWebSocket(req, object: WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { runOnUiThread { mulaiRekam() } }
            override fun onMessage(ws: WebSocket, text: String) {
                try { val j=JSONObject(text); val t=j.optJSONObject("channel")?.optJSONArray("alternatives")?.optJSONObject(0)?.optString("transcript").orEmpty(); if(t.isNotBlank()) runOnUiThread { subtitle.text=t } } catch (_:Exception) {}
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { runOnUiThread { subtitle.text="Koneksi gagal: ${t.message}"; berhenti() } }
        })
        aktif=true; tombol.text="BERHENTI"; subtitle.text="Menghubungkan…"
    }

    private fun mulaiRekam() {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        rekam = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,min*2)
        rekam?.startRecording(); subtitle.text="Mendengarkan…"
        Thread {
            val b=ByteArray(min)
            while(aktif) { val n=rekam?.read(b,0,b.size) ?: 0; if(n>0) socket?.send(ByteString.of(*b.copyOf(n))) }
        }.start()
    }

    private fun berhenti() {
        aktif=false
        try { rekam?.stop(); rekam?.release() } catch (_:Exception) {}
        rekam=null; socket?.close(1000,"selesai"); socket=null
        tombol.text="MULAI"
    }
    override fun onDestroy() { berhenti(); super.onDestroy() }
}
