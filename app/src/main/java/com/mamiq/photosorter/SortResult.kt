package com.mamiq.photosorter

data class SortResult(
    var totalDiperiksa: Int = 0,
    var berhasilDisalin: Int = 0,
    var dariExif: Int = 0,
    var dariNamaFile: Int = 0,
    var diabaikan: Int = 0,
    var diluarRentang: Int = 0,
    val folderTanggalDibuat: MutableSet<String> = mutableSetOf(),
    val daftarDiabaikan: MutableList<String> = mutableListOf(),
    val daftarError: MutableList<String> = mutableListOf()
)

/**
 * Rentang filter tanggal opsional. Jika null, semua foto diproses
 * tanpa filter (perilaku default/lama).
 * tahun/bulan/hari mengikuti kalender biasa (bulan 1-12).
 */
data class RentangTanggal(
    val tahunMulai: Int, val bulanMulai: Int, val hariMulai: Int,
    val tahunAkhir: Int, val bulanAkhir: Int, val hariAkhir: Int
) {
    private val angkaMulai = tahunMulai * 10000 + bulanMulai * 100 + hariMulai
    private val angkaAkhir = tahunAkhir * 10000 + bulanAkhir * 100 + hariAkhir

    fun cocok(hasil: HasilDeteksi): Boolean {
        val angka = hasil.keAngkaPembanding()
        return angka in angkaMulai..angkaAkhir
    }
}

/** Ekstensi foto yang diproses — sama seperti versi Python */
val EKSTENSI_FOTO = setOf(
    "jpg", "jpeg", "png", "heic", "heif",
    "tiff", "tif", "bmp", "webp", "gif", "dng", "raw"
)
