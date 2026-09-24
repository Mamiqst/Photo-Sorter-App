package com.mamiq.photosorter

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val folderSumberList = mutableListOf<Uri>()
    private var folderTujuanUri: Uri? = null

    private lateinit var layoutDaftarSumber: LinearLayout
    private lateinit var tvFolderTujuan: TextView
    private lateinit var radioGroupFormat: RadioGroup
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnMulai: Button
    private lateinit var checkboxFilterTanggal: CheckBox
    private lateinit var layoutFilterTanggal: LinearLayout
    private lateinit var btnTanggalMulai: Button
    private lateinit var btnTanggalAkhir: Button

    // Menyimpan tanggal mulai/akhir yang dipilih (null = belum dipilih)
    private var tanggalMulai: Triple<Int, Int, Int>? = null // tahun, bulan(1-12), hari
    private var tanggalAkhir: Triple<Int, Int, Int>? = null

    // Kode request untuk membedakan hasil folder picker
    private val REQUEST_PILIH_SUMBER = 1001
    private val REQUEST_PILIH_TUJUAN = 1002

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutDaftarSumber = findViewById(R.id.layoutDaftarSumber)
        tvFolderTujuan = findViewById(R.id.tvFolderTujuan)
        radioGroupFormat = findViewById(R.id.radioGroupFormat)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        btnMulai = findViewById(R.id.btnMulai)
        checkboxFilterTanggal = findViewById(R.id.checkboxFilterTanggal)
        layoutFilterTanggal = findViewById(R.id.layoutFilterTanggal)
        btnTanggalMulai = findViewById(R.id.btnTanggalMulai)
        btnTanggalAkhir = findViewById(R.id.btnTanggalAkhir)

        findViewById<Button>(R.id.btnTambahSumber).setOnClickListener {
            bukaFolderPicker(REQUEST_PILIH_SUMBER)
        }

        findViewById<Button>(R.id.btnPilihTujuan).setOnClickListener {
            bukaFolderPicker(REQUEST_PILIH_TUJUAN)
        }

        checkboxFilterTanggal.setOnCheckedChangeListener { _, dicentang ->
            layoutFilterTanggal.visibility = if (dicentang) View.VISIBLE else View.GONE
        }

        btnTanggalMulai.setOnClickListener {
            bukaDatePicker(tanggalMulai) { tahun, bulan, hari ->
                tanggalMulai = Triple(tahun, bulan, hari)
                btnTanggalMulai.text = formatTanggalTampilan(tahun, bulan, hari)
            }
        }

        btnTanggalAkhir.setOnClickListener {
            bukaDatePicker(tanggalAkhir) { tahun, bulan, hari ->
                tanggalAkhir = Triple(tahun, bulan, hari)
                btnTanggalAkhir.text = formatTanggalTampilan(tahun, bulan, hari)
            }
        }

        btnMulai.setOnClickListener {
            mulaiProsesSortir()
        }
    }

    private fun bukaDatePicker(nilaiAwal: Triple<Int, Int, Int>?, onDipilih: (Int, Int, Int) -> Unit) {
        val cal = Calendar.getInstance()
        if (nilaiAwal != null) {
            cal.set(nilaiAwal.first, nilaiAwal.second - 1, nilaiAwal.third)
        }
        DatePickerDialog(
            this,
            { _, tahun, bulanIndex, hari ->
                onDipilih(tahun, bulanIndex + 1, hari) // bulanIndex 0-11 -> disimpan 1-12
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatTanggalTampilan(tahun: Int, bulan: Int, hari: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", tahun, bulan, hari)
    }

    private fun bukaFolderPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data!!

        // Simpan izin akses permanen supaya tidak hilang setelah aplikasi ditutup
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        when (requestCode) {
            REQUEST_PILIH_SUMBER -> {
                folderSumberList.add(uri)
                tambahBarisFolderSumber(uri)
            }
            REQUEST_PILIH_TUJUAN -> {
                folderTujuanUri = uri
                tvFolderTujuan.text = namaTampilanFolder(uri)
            }
        }
    }

    private fun tambahBarisFolderSumber(uri: Uri) {
        val baris = TextView(this)
        baris.text = "• ${namaTampilanFolder(uri)}"
        baris.textSize = 13f
        baris.setPadding(0, 4, 0, 4)
        layoutDaftarSumber.addView(baris)
    }

    private fun namaTampilanFolder(uri: Uri): String {
        val doc = DocumentFile.fromTreeUri(this, uri)
        return doc?.name ?: uri.lastPathSegment ?: uri.toString()
    }

    private fun mulaiProsesSortir() {
        if (folderSumberList.isEmpty()) {
            Toast.makeText(this, "Tambahkan minimal satu folder sumber dulu.", Toast.LENGTH_SHORT).show()
            return
        }
        if (folderTujuanUri == null) {
            Toast.makeText(this, "Pilih folder tujuan dulu.", Toast.LENGTH_SHORT).show()
            return
        }

        val pakaiDash = radioGroupFormat.checkedRadioButtonId == R.id.radioFormatDash

        // Susun filter rentang tanggal jika diaktifkan, dengan validasi.
        var rentangTanggal: RentangTanggal? = null
        if (checkboxFilterTanggal.isChecked) {
            val mulai = tanggalMulai
            val akhir = tanggalAkhir
            if (mulai == null || akhir == null) {
                Toast.makeText(this, "Pilih tanggal mulai dan tanggal berakhir dulu.", Toast.LENGTH_SHORT).show()
                return
            }
            val angkaMulai = mulai.first * 10000 + mulai.second * 100 + mulai.third
            val angkaAkhir = akhir.first * 10000 + akhir.second * 100 + akhir.third
            if (angkaMulai > angkaAkhir) {
                Toast.makeText(this, "Tanggal mulai tidak boleh setelah tanggal berakhir.", Toast.LENGTH_SHORT).show()
                return
            }
            rentangTanggal = RentangTanggal(
                mulai.first, mulai.second, mulai.third,
                akhir.first, akhir.second, akhir.third
            )
        }

        btnMulai.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvStatus.text = "Mengumpulkan daftar file foto..."
        tvLog.text = ""

        // Jalankan proses berat di background thread supaya UI tidak macet
        Thread {
            val engine = PhotoSorterEngine(this)
            val semuaFoto = engine.kumpulkanSemuaFoto(folderSumberList)

            mainHandler.post {
                tvStatus.text = "Ditemukan ${semuaFoto.size} file foto. Memproses..."
            }

            val hasil = engine.prosesSortir(
                semuaFoto,
                folderTujuanUri!!,
                pakaiDash,
                rentangTanggal
            ) { index, total, namaFile ->
                mainHandler.post {
                    val persen = if (total > 0) (index * 100 / total) else 0
                    progressBar.progress = persen
                    tvStatus.text = "Memproses $index/$total: $namaFile"
                }
            }

            mainHandler.post {
                tampilkanRingkasan(hasil)
                btnMulai.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }.start()
    }

    private fun tampilkanRingkasan(hasil: SortResult) {
        val sb = StringBuilder()
        sb.appendLine("=== RINGKASAN HASIL ===")
        sb.appendLine("Total diperiksa       : ${hasil.totalDiperiksa}")
        sb.appendLine("Berhasil disalin       : ${hasil.berhasilDisalin}")
        sb.appendLine("  - dari EXIF          : ${hasil.dariExif}")
        sb.appendLine("  - dari nama file     : ${hasil.dariNamaFile}")
        sb.appendLine("Diabaikan              : ${hasil.diabaikan}")
        if (hasil.diluarRentang > 0) {
            sb.appendLine("  - di luar rentang tanggal : ${hasil.diluarRentang}")
        }
        sb.appendLine()
        sb.appendLine("Folder tanggal dibuat (${hasil.folderTanggalDibuat.size}):")
        if (hasil.folderTanggalDibuat.isEmpty()) {
            sb.appendLine("  (tidak ada)")
        } else {
            hasil.folderTanggalDibuat.sorted().forEach { sb.appendLine("  - $it") }
        }

        if (hasil.daftarDiabaikan.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("File diabaikan (${hasil.daftarDiabaikan.size}), maks 20 ditampilkan:")
            hasil.daftarDiabaikan.take(20).forEach { sb.appendLine("  - $it") }
            if (hasil.daftarDiabaikan.size > 20) {
                sb.appendLine("  ... dan ${hasil.daftarDiabaikan.size - 20} lainnya")
            }
        }

        if (hasil.daftarError.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Error (${hasil.daftarError.size}):")
            hasil.daftarError.take(20).forEach { sb.appendLine("  - $it") }
        }

        sb.appendLine()
        sb.appendLine("Selesai. File asli TIDAK dihapus/dipindahkan (hanya disalin).")

        tvLog.text = sb.toString()
        tvStatus.text = "Selesai."
    }
}
