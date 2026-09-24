package com.mamiq.photosorter

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Sumber tanggal yang berhasil dideteksi, untuk statistik ringkasan.
 */
enum class SumberTanggal {
    EXIF, NAMA_FILE
}

data class HasilDeteksi(
    val tahun: Int,
    val bulan: Int,   // 1-12
    val hari: Int,
    val sumber: SumberTanggal
) {
    /** Representasi angka YYYYMMDD supaya mudah dibandingkan untuk filter rentang. */
    fun keAngkaPembanding(): Int = tahun * 10000 + bulan * 100 + hari
}

object DateExtractor {

    // Pola sama persis dengan skrip Python:
    // (?<!\d)(20\d{2}|19\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])(?!\d)
    private val POLA_TANGGAL: Pattern = Pattern.compile(
        "(?<!\\d)(20\\d{2}|19\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])(?!\\d)"
    )

    private val EXIF_FORMAT_1 = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val EXIF_FORMAT_2 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Coba baca DateTimeOriginal (prioritas), fallback ke DateTime biasa.
     * Mengembalikan null jika tidak ada / gagal parse.
     */
    fun ambilTanggalExif(context: Context, uri: Uri): HasilDeteksi? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)

                // Prioritas 1: DateTimeOriginal
                var tanggalStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)

                // Fallback: DateTime biasa (waktu modifikasi kamera)
                if (tanggalStr.isNullOrBlank()) {
                    tanggalStr = exif.getAttribute(ExifInterface.TAG_DATETIME)
                }

                if (tanggalStr.isNullOrBlank()) return null

                val parsed = try {
                    EXIF_FORMAT_1.parse(tanggalStr)
                } catch (e: Exception) {
                    try {
                        EXIF_FORMAT_2.parse(tanggalStr)
                    } catch (e2: Exception) {
                        null
                    }
                } ?: return null

                val cal = Calendar.getInstance()
                cal.time = parsed
                HasilDeteksi(
                    tahun = cal.get(Calendar.YEAR),
                    bulan = cal.get(Calendar.MONTH) + 1,
                    hari = cal.get(Calendar.DAY_OF_MONTH),
                    sumber = SumberTanggal.EXIF
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cari pola YYYYMMDD di nama file. Contoh valid:
     *   IMG-20260912-WA0001.jpg
     *   20260701_123456.jpg
     */
    fun ambilTanggalDariNamaFile(namaFile: String): HasilDeteksi? {
        val matcher = POLA_TANGGAL.matcher(namaFile)
        if (!matcher.find()) return null

        val tahun = matcher.group(1)?.toIntOrNull() ?: return null
        val bulan = matcher.group(2)?.toIntOrNull() ?: return null
        val hari = matcher.group(3)?.toIntOrNull() ?: return null

        // Validasi tanggal benar-benar valid (misal bukan 30 Februari)
        if (!tanggalValid(tahun, bulan, hari)) return null

        return HasilDeteksi(tahun, bulan, hari, SumberTanggal.NAMA_FILE)
    }

    private fun tanggalValid(tahun: Int, bulan: Int, hari: Int): Boolean {
        return try {
            val cal = Calendar.getInstance()
            cal.isLenient = false
            cal.set(tahun, bulan - 1, hari)
            cal.time // memicu validasi
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Prioritas sesuai spesifikasi:
     *  1. EXIF DateTimeOriginal / DateTime
     *  2. Pola YYYYMMDD di nama file
     *  3. null -> file harus di-skip
     */
    fun tentukanTanggal(context: Context, uri: Uri, namaFile: String): HasilDeteksi? {
        ambilTanggalExif(context, uri)?.let { return it }
        return ambilTanggalDariNamaFile(namaFile)
    }

    fun formatNamaFolder(hasil: HasilDeteksi, pakaiDash: Boolean): String {
        val b = String.format(Locale.US, "%02d", hasil.bulan)
        val d = String.format(Locale.US, "%02d", hasil.hari)
        return if (pakaiDash) {
            "${hasil.tahun}-$b-$d"
        } else {
            "${hasil.tahun}$b$d"
        }
    }
}
