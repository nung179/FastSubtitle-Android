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
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var apiKey: EditText
    private lateinit var bahasaSumber: Spinner
    private lateinit var bahasaTujuan: Spinner
    private lateinit var warnaSubtitle: Spinner

    private lateinit var tombol: Button
    private lateinit var status: TextView

    private val daftarBahasa =
        arrayOf(
            "Mandarin",
            "Indonesia",
            "Inggris",
            "Jepang",
            "Korea"
        )

    private val kodeBahasa =
        arrayOf(
            "zh",
            "id",
            "en",
            "ja",
            "ko"
        )

    private val daftarWarna =
        arrayOf(
            "Kuning",
            "Putih"
        )

    private val preferensi by lazy {
        getSharedPreferences(
            "fastsubtitle",
            MODE_PRIVATE
        )
    }

    private val permintaanCapture =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { hasil ->

            if (
                hasil.resultCode ==
                Activity.RESULT_OK &&
                hasil.data != null
            ) {

                simpanPengaturan()

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
                            apiKey.text
                                .toString()
                                .trim()
                        )

                        putExtra(
                            SubtitleService.EXTRA_BAHASA_SUMBER,
                            kodeBahasa[
                                bahasaSumber
                                    .selectedItemPosition
                            ]
                        )

                        putExtra(
                            SubtitleService.EXTRA_BAHASA_TUJUAN,
                            kodeBahasa[
                                bahasaTujuan
                                    .selectedItemPosition
                            ]
                        )

                        putExtra(
                            SubtitleService.EXTRA_WARNA,
                            if (
                                warnaSubtitle
                                    .selectedItemPosition == 0
                            ) {
                                "kuning"
                            } else {
                                "putih"
                            }
                        )
                    }

                ContextCompat
                    .startForegroundService(
                        this,
                        intentService
                    )

                status.text =
                    "Fast Subtitle aktif.\nBuka video yang ingin diterjemahkan."

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

        super.onCreate(
            savedInstanceState
        )

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

                setBackgroundColor(
                    Color.rgb(
                        20,
                        20,
                        20
                    )
                )
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

                gravity =
                    Gravity.CENTER
            }
        )

        root.addView(
            TextView(this).apply {

                text =
                    "Audio Internal → Subtitle Terjemahan"

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.LTGRAY
                )
            }
        )

        apiKey =
            EditText(this).apply {

                hint =
                    "API Key Deepgram"

                setSingleLine(
                    true
                )

                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD

                setText(
                    preferensi.getString(
                        "api_key",
                        ""
                    )
                )
            }

        root.addView(
            apiKey,
            parameterLebar().apply {
                topMargin =
                    30
            }
        )

        root.addView(
            judulField(
                "Bahasa suara"
            )
        )

        bahasaSumber =
            buatSpinner(
                daftarBahasa
            )

        bahasaSumber.setSelection(
            preferensi.getInt(
                "bahasa_sumber",
                0
            )
        )

        root.addView(
            bahasaSumber,
            parameterLebar()
        )

        root.addView(
            judulField(
                "Terjemahkan ke"
            )
        )

        bahasaTujuan =
            buatSpinner(
                daftarBahasa
            )

        bahasaTujuan.setSelection(
            preferensi.getInt(
                "bahasa_tujuan",
                1
            )
        )

        root.addView(
            bahasaTujuan,
            parameterLebar()
        )

        root.addView(
            judulField(
                "Warna subtitle"
            )
        )

        warnaSubtitle =
            buatSpinner(
                daftarWarna
            )

        warnaSubtitle.setSelection(
            preferensi.getInt(
                "warna",
                0
            )
        )

        root.addView(
            warnaSubtitle,
            parameterLebar()
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
            parameterLebar().apply {

                topMargin =
                    28
            }
        )

        status =
            TextView(this).apply {

                text =
                    "Pilih bahasa, lalu tekan MULAI."

                textSize =
                    18f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    10,
                    30,
                    10,
                    10
                )
            }

        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(
            root
        )
    }

    private fun judulField(
        teks: String
    ): TextView {

        return TextView(this).apply {

            text =
                teks

            textSize =
                14f

            setTextColor(
                Color.LTGRAY
            )

            setPadding(
                0,
                20,
                0,
                6
            )
        }
    }

    private fun buatSpinner(
        isi: Array<String>
    ): Spinner {

        return Spinner(this).apply {

            adapter =
                ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    isi
                )
        }
    }

    private fun parameterLebar():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun simpanPengaturan() {

        preferensi.edit()
            .putString(
                "api_key",
                apiKey.text
                    .toString()
                    .trim()
            )
            .putInt(
                "bahasa_sumber",
                bahasaSumber
                    .selectedItemPosition
            )
            .putInt(
                "bahasa_tujuan",
                bahasaTujuan
                    .selectedItemPosition
            )
            .putInt(
                "warna",
                warnaSubtitle
                    .selectedItemPosition
            )
            .apply()
    }

    private fun mulai() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {

            Toast.makeText(
                this,
                "Minimal Android 10.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (
            apiKey.text
                .toString()
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
            bahasaSumber
                .selectedItemPosition ==
            bahasaTujuan
                .selectedItemPosition
        ) {

            Toast.makeText(
                this,
                "Bahasa sumber dan tujuan jangan sama.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            ActivityCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat
                .requestPermissions(
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
            ActivityCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat
                .requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    101
                )
        }

        if (
            !Settings.canDrawOverlays(
                this
            )
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
            manager
                .createScreenCaptureIntent()
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
