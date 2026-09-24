package com.mamiq.photosorter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Engine utama: port logika dari skrip Python sortir_foto.py
 * ke Android, menggunakan Storage Access Framework (SAF) sehingga
 * bisa mengakses folder mana pun di HP tanpa permission storage penuh.
 */
class PhotoSorterEngine(private val context: Context) {

    /**
     * Kumpulkan semua file foto dari daftar folder sumber secara rekursif.
     */
    fun kumpulkanSemuaFoto(folderSumberUris: List<Uri>): List<DocumentFile> {
        val hasil = mutableListOf<DocumentFile>()
        for (uri in folderSumberUris) {
            val root = DocumentFile.fromTreeUri(context, uri) ?: continue
            scanRekursif(root, hasil)
        }
        return hasil
    }

    private fun scanRekursif(folder: DocumentFile, hasil: MutableList<DocumentFile>) {
        val anak = folder.listFiles()
        for (item in anak) {
            if (item.isDirectory) {
                scanRekursif(item, hasil)
            } else if (item.isFile) {
                val nama = item.name ?: continue
                val ekstensi = nama.substringAfterLast('.', "").lowercase()
                if (ekstensi in EKSTENSI_FOTO) {
                    hasil.add(item)
                }
            }
        }
    }

    /**
     * Proses utama: untuk setiap file foto, tentukan tanggal, buat folder
     * tanggal di tujuan jika belum ada, lalu copy dengan penanganan konflik nama.
     *
     * onProgress dipanggil setiap file selesai diproses (index, total, namaFileSaatIni).
     */
    fun prosesSortir(
        fileFotoList: List<DocumentFile>,
        folderTujuanUri: Uri,
        pakaiDash: Boolean,
        rentangTanggal: RentangTanggal? = null,
        onProgress: (Int, Int, String) -> Unit
    ): SortResult {
        val hasil = SortResult()
        hasil.totalDiperiksa = fileFotoList.size

        val folderTujuanRoot = DocumentFile.fromTreeUri(context, folderTujuanUri)
            ?: return hasil.apply {
                daftarError.add("Folder tujuan tidak dapat diakses.")
            }

        // Cache folder tanggal yang sudah dibuat supaya tidak query berulang-ulang
        val cacheFolderTanggal = mutableMapOf<String, DocumentFile>()

        fileFotoList.forEachIndexed { index, fileFoto ->
            val namaFile = fileFoto.name ?: "file_tanpa_nama_$index"
            onProgress(index + 1, fileFotoList.size, namaFile)

            try {
                val deteksi = DateExtractor.tentukanTanggal(context, fileFoto.uri, namaFile)

                if (deteksi == null) {
                    hasil.diabaikan++
                    hasil.daftarDiabaikan.add(namaFile)
                    return@forEachIndexed
                }

                // Filter rentang tanggal opsional — di luar rentang dianggap
                // sama seperti file diabaikan (tidak disalin).
                if (rentangTanggal != null && !rentangTanggal.cocok(deteksi)) {
                    hasil.diabaikan++
                    hasil.diluarRentang++
                    hasil.daftarDiabaikan.add("$namaFile (di luar rentang tanggal)")
                    return@forEachIndexed
                }

                when (deteksi.sumber) {
                    SumberTanggal.EXIF -> hasil.dariExif++
                    SumberTanggal.NAMA_FILE -> hasil.dariNamaFile++
                }

                val namaFolderTanggal = DateExtractor.formatNamaFolder(deteksi, pakaiDash)

                val folderTanggalDoc = cacheFolderTanggal.getOrPut(namaFolderTanggal) {
                    folderTujuanRoot.findFile(namaFolderTanggal)
                        ?: folderTujuanRoot.createDirectory(namaFolderTanggal)
                        ?: throw Exception("Gagal membuat folder $namaFolderTanggal")
                }

                hasil.folderTanggalDibuat.add(namaFolderTanggal)

                val namaTujuanUnik = namaUnikJikaKonflik(folderTanggalDoc, namaFile)
                val mimeType = fileFoto.type ?: "image/*"
                val fileTujuan = folderTanggalDoc.createFile(mimeType, namaTujuanUnik)
                    ?: throw Exception("Gagal membuat file tujuan $namaTujuanUnik")

                salinIsiFile(fileFoto.uri, fileTujuan.uri)
                hasil.berhasilDisalin++

            } catch (e: Exception) {
                hasil.diabaikan++
                hasil.daftarError.add("$namaFile -> ${e.message}")
            }
        }

        return hasil
    }

    /**
     * Cek apakah nama file sudah ada di folder tujuan. Jika ya, tambahkan
     * akhiran _1, _2, dst sebelum ekstensi — sama seperti skrip Python.
     */
    private fun namaUnikJikaKonflik(folderTujuan: DocumentFile, namaAsli: String): String {
        if (folderTujuan.findFile(namaAsli) == null) return namaAsli

        val titikIndex = namaAsli.lastIndexOf('.')
        val stem = if (titikIndex != -1) namaAsli.substring(0, titikIndex) else namaAsli
        val ekstensi = if (titikIndex != -1) namaAsli.substring(titikIndex) else ""

        var counter = 1
        while (true) {
            val namaBaru = "${stem}_$counter$ekstensi"
            if (folderTujuan.findFile(namaBaru) == null) return namaBaru
            counter++
        }
    }

    private fun salinIsiFile(sumberUri: Uri, tujuanUri: Uri) {
        context.contentResolver.openInputStream(sumberUri)?.use { input ->
            context.contentResolver.openOutputStream(tujuanUri)?.use { output ->
                input.copyTo(output, bufferSize = 8 * 1024)
            }
        }
    }
}
