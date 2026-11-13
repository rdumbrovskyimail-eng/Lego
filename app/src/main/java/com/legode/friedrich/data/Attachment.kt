package com.legode.friedrich.data

import com.legode.friedrich.core.long
import com.legode.friedrich.core.objects
import com.legode.friedrich.core.str
import org.json.JSONArray
import org.json.JSONObject

/** Вложение сообщения. Файл всегда лежит в приватной папке приложения (files/att). */
data class Attachment(
    /** image | audio | pdf | text */
    val type: String,
    val path: String,
    val mime: String,
    val name: String,
    val size: Long,
    val durationMs: Long = 0
) {
    val isImage: Boolean get() = type == TYPE_IMAGE
    val isAudio: Boolean get() = type == TYPE_AUDIO
    val isPdf: Boolean get() = type == TYPE_PDF
    val isText: Boolean get() = type == TYPE_TEXT

    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("path", path)
        .put("mime", mime)
        .put("name", name)
        .put("size", size)
        .put("durationMs", durationMs)

    /** Короткая подпись для превью и для текстовой истории, когда сам файл в модель не отправляется. */
    fun label(): String = when (type) {
        TYPE_IMAGE -> "📷 фото"
        TYPE_AUDIO -> "🎤 голосовое"
        TYPE_PDF -> "📄 $name"
        else -> "📎 $name"
    }

    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_AUDIO = "audio"
        const val TYPE_PDF = "pdf"
        const val TYPE_TEXT = "text"

        fun listFromJson(json: String): List<Attachment> {
            if (json.isBlank()) return emptyList()
            return try {
                JSONArray(json).objects().mapNotNull { o ->
                    val path = o.str("path")
                    if (path.isEmpty()) null else Attachment(
                        type = o.str("type", TYPE_TEXT),
                        path = path,
                        mime = o.str("mime", "application/octet-stream"),
                        name = o.str("name", "файл"),
                        size = o.long("size"),
                        durationMs = o.long("durationMs")
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun listToJson(list: List<Attachment>): String {
            if (list.isEmpty()) return ""
            val arr = JSONArray()
            for (a in list) arr.put(a.toJson())
            return arr.toString()
        }
    }
}
