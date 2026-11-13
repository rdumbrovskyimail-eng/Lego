package com.legode.friedrich.core

import org.json.JSONArray
import org.json.JSONObject

/*
 * Безопасные обёртки над org.json.
 *
 * Важно: на Android JSONObject.optString() для JSON-null возвращает строку "null",
 * а модель иногда отдаёт числа строками и наоборот. Все чтения ответа модели идут
 * через эти функции, чтобы «кривой» JSON никогда не ронял приложение.
 */

fun JSONObject.str(key: String, def: String = ""): String {
    if (!has(key) || isNull(key)) return def
    return when (val v = opt(key)) {
        null -> def
        is String -> v
        is JSONObject, is JSONArray -> def
        else -> v.toString()
    }
}

fun JSONObject.int(key: String, def: Int = 0): Int {
    if (!has(key) || isNull(key)) return def
    return when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull() ?: v.trim().toDoubleOrNull()?.toInt() ?: def
        is Boolean -> if (v) 1 else 0
        else -> def
    }
}

fun JSONObject.long(key: String, def: Long = 0L): Long {
    if (!has(key) || isNull(key)) return def
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong() ?: def
        else -> def
    }
}

fun JSONObject.dbl(key: String, def: Double = 0.0): Double {
    if (!has(key) || isNull(key)) return def
    return when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.trim().replace(',', '.').toDoubleOrNull() ?: def
        else -> def
    }
}

fun JSONObject.bool(key: String, def: Boolean = false): Boolean {
    if (!has(key) || isNull(key)) return def
    return when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (v.trim().lowercase()) {
            "true", "yes", "1", "ok", "да", "ja", "richtig" -> true
            "false", "no", "0", "нет", "nein", "falsch" -> false
            else -> def
        }
        else -> def
    }
}

fun JSONObject.obj(key: String): JSONObject? =
    if (has(key) && !isNull(key)) optJSONObject(key) else null

fun JSONObject.arr(key: String): JSONArray? =
    if (has(key) && !isNull(key)) optJSONArray(key) else null

fun JSONArray.objects(): List<JSONObject> {
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i)
        if (o != null) out.add(o)
    }
    return out
}

fun JSONArray.strings(): List<String> {
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        if (isNull(i)) continue
        when (val v = opt(i)) {
            null -> Unit
            is String -> out.add(v)
            is JSONObject, is JSONArray -> Unit
            else -> out.add(v.toString())
        }
    }
    return out
}

fun jsonArrayOfStrings(items: Collection<String>): JSONArray {
    val a = JSONArray()
    for (s in items) a.put(s)
    return a
}

fun parseStringList(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        JSONArray(json).strings()
    } catch (_: Exception) {
        emptyList()
    }
}

fun parseObjectOrNull(json: String): JSONObject? {
    if (json.isBlank()) return null
    return try {
        JSONObject(json)
    } catch (_: Exception) {
        null
    }
}
