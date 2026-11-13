package com.legode.friedrich.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.legode.friedrich.core.Logx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Приём и хранение вложений.
 *
 * Любой файл копируется в приватную папку приложения и приводится к виду, который понимает Gemini:
 *  - фото уменьшается до 1600 px, поворачивается по EXIF и сохраняется в JPEG;
 *  - текстовые файлы (txt, csv, md, json, srt…) и DOCX превращаются в обычный текст;
 *  - PDF и аудио копируются как есть.
 */
class AttachmentStore(private val context: Context) {

    sealed class ImportResult {
        data class Ok(val attachment: Attachment) : ImportResult()
        data class Rejected(val reason: String) : ImportResult()
    }

    private fun dir(): File {
        val d = File(context.filesDir, "att")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fresh(ext: String): File = File(dir(), "${UUID.randomUUID()}.$ext")

    // ───────────────────────── Импорт из системного выбора файлов ─────────────────────────

    suspend fun importUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val tmp = File(dir(), "tmp_${UUID.randomUUID()}")
        try {
            val (name, _) = queryMeta(uri)
            val ext = name.substringAfterLast('.', "").lowercase()
            val mime = (context.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream").lowercase()

            val copied = copyCapped(uri, tmp, MAX_RAW_BYTES)
            if (!copied) {
                return@withContext ImportResult.Rejected("Файл «$name» слишком большой (предел 30 МБ).")
            }
            if (tmp.length() == 0L) {
                return@withContext ImportResult.Rejected("Файл «$name» пустой или недоступен.")
            }

            when {
                mime.startsWith("image/") || ext in IMAGE_EXT -> {
                    val out = fresh("jpg")
                    if (!normalizeImage(tmp, out)) {
                        out.delete()
                        return@withContext ImportResult.Rejected("Не получилось прочитать картинку «$name».")
                    }
                    ImportResult.Ok(
                        Attachment(Attachment.TYPE_IMAGE, out.absolutePath, "image/jpeg", name, out.length())
                    )
                }

                mime == "application/pdf" || ext == "pdf" -> {
                    if (tmp.length() > MAX_BINARY_BYTES) {
                        return@withContext ImportResult.Rejected("PDF «$name» больше 15 МБ — пришли по частям.")
                    }
                    val out = fresh("pdf")
                    tmp.copyTo(out, overwrite = true)
                    ImportResult.Ok(Attachment(Attachment.TYPE_PDF, out.absolutePath, "application/pdf", name, out.length()))
                }

                mime.startsWith("audio/") || ext in AUDIO_EXT -> {
                    if (tmp.length() > MAX_BINARY_BYTES) {
                        return@withContext ImportResult.Rejected("Аудио «$name» больше 15 МБ.")
                    }
                    val out = fresh(if (ext.isNotEmpty()) ext else "audio")
                    tmp.copyTo(out, overwrite = true)
                    ImportResult.Ok(
                        Attachment(Attachment.TYPE_AUDIO, out.absolutePath, normalizeAudioMime(mime, ext), name, out.length())
                    )
                }

                ext == "docx" || mime.contains("wordprocessingml") -> {
                    val text = docxText(tmp).trim()
                    if (text.isEmpty()) {
                        return@withContext ImportResult.Rejected("В документе «$name» не нашлось текста.")
                    }
                    saveText(text, name)
                }

                mime.startsWith("text/") || mime in TEXT_MIMES || ext in TEXT_EXT || looksLikeText(tmp) -> {
                    val text = readTextCapped(tmp).trim()
                    if (text.isEmpty()) {
                        return@withContext ImportResult.Rejected("Файл «$name» пустой.")
                    }
                    saveText(text, name)
                }

                else -> ImportResult.Rejected(
                    "Такой файл («$name») Friedrich не читает. Подойдут: фото, PDF, аудио, текст, DOCX."
                )
            }
        } catch (t: Throwable) {
            Logx.w("importUri failed", t)
            ImportResult.Rejected("Не удалось открыть файл: ${t.message ?: "ошибка доступа"}")
        } finally {
            tmp.delete()
        }
    }

    /** Текст, которым поделились из другого приложения или вставили как «список». */
    suspend fun importPlainText(text: String, name: String): ImportResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) ImportResult.Rejected("Пустой текст.") else saveText(text.trim(), name)
    }

    private fun saveText(text: String, name: String): ImportResult {
        val clipped = if (text.length > MAX_TEXT_CHARS) text.substring(0, MAX_TEXT_CHARS) else text
        val out = fresh("txt")
        out.writeText(clipped, Charsets.UTF_8)
        return ImportResult.Ok(Attachment(Attachment.TYPE_TEXT, out.absolutePath, "text/plain", name, out.length()))
    }

    // ───────────────────────── Камера и диктофон ─────────────────────────

    /** Куда камера должна положить снимок: файл и content-Uri для него. */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(dir(), "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return file to uri
    }

    suspend fun adoptCameraFile(raw: File): Attachment? = withContext(Dispatchers.IO) {
        try {
            if (!raw.exists() || raw.length() == 0L) return@withContext null
            val out = fresh("jpg")
            val ok = normalizeImage(raw, out)
            raw.delete()
            if (!ok) {
                out.delete()
                null
            } else {
                Attachment(Attachment.TYPE_IMAGE, out.absolutePath, "image/jpeg", "Фото с камеры", out.length())
            }
        } catch (t: Throwable) {
            Logx.w("adoptCameraFile failed", t)
            null
        }
    }

    fun newVoiceFile(): File = File(dir(), "voice_${System.currentTimeMillis()}.aac")

    fun voiceAttachment(file: File, durationMs: Long): Attachment =
        Attachment(Attachment.TYPE_AUDIO, file.absolutePath, "audio/aac", "Голосовое сообщение", file.length(), durationMs)

    // ───────────────────────── Чтение для отправки в модель ─────────────────────────

    suspend fun readBase64(a: Attachment): String? = withContext(Dispatchers.IO) {
        try {
            val f = File(a.path)
            if (!f.exists()) null else Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        } catch (t: Throwable) {
            Logx.w("readBase64 failed", t)
            null
        }
    }

    suspend fun readText(a: Attachment): String = withContext(Dispatchers.IO) {
        try {
            val f = File(a.path)
            if (!f.exists()) "" else readTextCapped(f)
        } catch (_: Throwable) {
            ""
        }
    }

    // ───────────────────────── Удаление ─────────────────────────

    fun delete(a: Attachment) {
        try {
            File(a.path).delete()
        } catch (_: Throwable) {
        }
    }

    fun deleteAll(jsonLists: List<String>) {
        for (json in jsonLists) for (a in Attachment.listFromJson(json)) delete(a)
    }

    fun wipe() {
        try {
            dir().listFiles()?.forEach { it.delete() }
        } catch (_: Throwable) {
        }
    }

    // ───────────────────────── Внутреннее ─────────────────────────

    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name = ""
        var size = -1L
        try {
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni) ?: ""
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Throwable) {
        }
        if (name.isEmpty()) name = uri.lastPathSegment?.substringAfterLast('/') ?: "файл"
        return name to size
    }

    private fun copyCapped(uri: Uri, dst: File, cap: Long): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return true
        input.use { ins ->
            FileOutputStream(dst).use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > cap) return false
                    out.write(buf, 0, n)
                }
            }
        }
        return true
    }

    private fun normalizeImage(src: File, dst: File): Boolean {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_DIM) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        var bmp: Bitmap = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return false

        val longest = max(bmp.width, bmp.height)
        if (longest > MAX_IMAGE_DIM) {
            val k = MAX_IMAGE_DIM.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * k).roundToInt().coerceAtLeast(1),
                (bmp.height * k).roundToInt().coerceAtLeast(1),
                true
            )
            if (scaled !== bmp) {
                bmp.recycle()
                bmp = scaled
            }
        }

        val degrees = exifDegrees(src)
        if (degrees != 0) {
            val m = Matrix()
            m.postRotate(degrees.toFloat())
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) {
                bmp.recycle()
                bmp = rotated
            }
        }

        FileOutputStream(dst).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 86, out) }
        bmp.recycle()
        return dst.length() > 0
    }

    private fun exifDegrees(file: File): Int = try {
        when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Throwable) {
        0
    }

    private fun readTextCapped(file: File): String {
        val bytes = FileInputStream(file).use { ins ->
            val cap = MAX_TEXT_CHARS * 4
            val buf = ByteArray(minOf(file.length(), cap.toLong()).toInt())
            var off = 0
            while (off < buf.size) {
                val n = ins.read(buf, off, buf.size - off)
                if (n < 0) break
                off += n
            }
            buf.copyOf(off)
        }
        var text = String(bytes, Charsets.UTF_8)
        // UTF-8 BOM и «битые» кодировки: если слишком много символов замены — пробуем windows-1251
        if (text.isNotEmpty() && text[0] == BOM) text = text.substring(1)
        val bad = text.count { it == REPLACEMENT_CHAR }
        if (bad > text.length / 20) {
            text = try {
                String(bytes, charset("windows-1251"))
            } catch (_: Throwable) {
                text
            }
        }
        return if (text.length > MAX_TEXT_CHARS) text.substring(0, MAX_TEXT_CHARS) else text
    }

    private fun looksLikeText(file: File): Boolean {
        return try {
            val buf = ByteArray(4096)
            val n = FileInputStream(file).use { it.read(buf) }
            if (n <= 0) return false
            for (i in 0 until n) if (buf[i].toInt() == 0) return false
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun docxText(file: File): String {
        ZipInputStream(FileInputStream(file)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = String(zip.readBytes(), Charsets.UTF_8)
                    return xml
                        .replace("</w:p>", "\n")
                        .replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("<w:br[^>]*/>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                }
                entry = zip.nextEntry
            }
        }
        return ""
    }

    private fun normalizeAudioMime(mime: String, ext: String): String = when {
        mime == "audio/mpeg" || mime == "audio/mpeg3" || ext == "mp3" -> "audio/mp3"
        mime == "audio/x-wav" || mime == "audio/wave" || mime == "audio/vnd.wave" || ext == "wav" -> "audio/wav"
        mime == "audio/aac-adts" || mime == "audio/x-aac" || ext == "aac" -> "audio/aac"
        mime == "audio/x-m4a" || mime == "audio/m4a" || ext == "m4a" -> "audio/mp4"
        mime == "audio/x-flac" || ext == "flac" -> "audio/flac"
        mime == "application/ogg" || ext == "ogg" || ext == "oga" || ext == "opus" -> "audio/ogg"
        mime.startsWith("audio/") -> mime
        else -> "audio/mp3"
    }

    companion object {
        private const val MAX_RAW_BYTES = 30L * 1024 * 1024
        private const val MAX_BINARY_BYTES = 15L * 1024 * 1024
        private const val MAX_TEXT_CHARS = 120_000
        private const val MAX_IMAGE_DIM = 1600

        /** Невидимые символы задаём кодом, а не литералом, — так они не теряются при копировании файла. */
        private val BOM = Char(0xFEFF)
        private val REPLACEMENT_CHAR = Char(0xFFFD)

        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
        private val AUDIO_EXT = setOf("mp3", "wav", "aac", "m4a", "ogg", "oga", "opus", "flac", "amr", "3gp")
        private val TEXT_EXT = setOf("txt", "md", "csv", "tsv", "json", "srt", "log", "xml", "html", "htm", "rtf")
        private val TEXT_MIMES = setOf(
            "application/json", "application/xml", "application/csv", "application/x-subrip", "application/rtf"
        )

        /** Картинка для экрана: декодирование с уменьшением под нужный размер. */
        fun decodeForDisplay(path: String, maxPx: Int): Bitmap? {
            return try {
                val bounds = BitmapFactory.Options()
                bounds.inJustDecodeBounds = true
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
                val opts = BitmapFactory.Options()
                opts.inSampleSize = sample
                BitmapFactory.decodeFile(path, opts)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
