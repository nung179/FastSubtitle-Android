package com.fastsubtitle.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var apiKey: EditText
    private lateinit var tombol: Button
    private lateinit var status: TextView

    private val permintaanCapture =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { hasil ->

            if (hasil.resultCode == Activity.RESULT_OK && hasil.data != null) {

                val intentService =
                    Intent(
                        this,
                        SubtitleService::class.java
                    ).apply {

                        putExtra(
                            SubtitleService.EXTRA_RESULT_CODE,
                            hasil.resultCode
                        )

                        putExtra(
                            SubtitleService.EXTRA_RESULT_DATA,
                            hasil.data
                        )

                        putExtra(
                            SubtitleService.EXTRA_API_KEY,
                            apiKey.text.toString().trim()
                        )
                    }

                ContextCompat.startForegroundService(
                    this,
                    intentService
                )

                status.text =
                    "Fast Subtitle aktif.\nBuka YouTube atau aplikasi video."

                tombol.text =
                    "BERHENTI"

            } else {

                status.text =
                    "Izin menangkap audio dibatalkan."
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    32,
                    48,
                    32,
                    32
                )

                gravity =
                    Gravity.CENTER_HORIZONTAL
            }

        root.addView(
            TextView(this).apply {

                text =
                    "Fast Subtitle"

                textSize =
                    30f

                setTextColor(
                    Color.WHITE
                )
            }
        )

        root.addView(
            TextView(this).apply {

                text =
                    "Audio Internal → Subtitle Online"

                textSize =
                    16f

                setTextColor(
                    Color.LTGRAY
                )
            }
        )

        apiKey =
            EditText(this).apply {

                hint =
                    "Tempel API Key Deepgram"

                setSingleLine(
                    true
                )
            }

        root.addView(
            apiKey,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                topMargin =
                    36
            }
        )

        tombol =
            Button(this).apply {

                text =
                    "MULAI"

                setOnClickListener {

                    if (
                        text.toString() ==
                        "BERHENTI"
                    ) {

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

                topMargin =
                    20
            }
        )

        status =
            TextView(this).apply {

                text =
                    "Subtitle akan tampil mengambang di atas aplikasi lain."

                textSize =
                    22f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )
            }

        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {

                topMargin =
                    30
            }
        )

        setContentView(
            root
        )
    }

    private fun mulai() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {

            Toast.makeText(
                this,
                "Audio internal membutuhkan Android 10 atau lebih baru.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (
            apiKey.text.toString()
                .trim()
                .isEmpty()
        ) {

            Toast.makeText(
                this,
                "Masukkan API Key Deepgram.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                100
            )

            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                101
            )
        }

        if (
            !Settings.canDrawOverlays(this)
        ) {

            Toast.makeText(
                this,
                "Aktifkan izin tampil di atas aplikasi lain.",
                Toast.LENGTH_LONG
            ).show()

            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(
                        "package:$packageName"
                    )
                )
            )

            return
        }

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        permintaanCapture.launch(
            manager.createScreenCaptureIntent()
        )
    }

    private fun berhenti() {

        val intent =
            Intent(
                this,
                SubtitleService::class.java
            ).apply {

                action =
                    SubtitleService.AKSI_BERHENTI
            }

        startService(
            intent
        )

        tombol.text =
            "MULAI"

        status.text =
            "Fast Subtitle dihentikan."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            mulai()
        }
    }
}
