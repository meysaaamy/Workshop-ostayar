package com.workshop.ledger.data.backup

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections

/** A JSON number remains its original lexical value, never a floating-point primitive. */
sealed interface JsonValue {
    data object Null : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data class Number(val raw: String) : JsonValue
    data class Text(val value: String) : JsonValue
    data class Array(val values: List<JsonValue>) : JsonValue
    data class Object(val fields: Map<String, JsonValue>) : JsonValue
}

class BackupReadException(val reason: Reason) : IllegalArgumentException(reason.name) {
    enum class Reason { SIZE_LIMIT, INVALID_UTF8, INVALID_JSON, RESOURCE_LIMIT, UNSUPPORTED_ENVELOPE, INVALID_SHAPE }
}

enum class LegacyBackupMode { FULL, PROJECT }
data class LegacyBackupDocument(val root: JsonValue.Object, val payload: JsonValue.Object, val mode: LegacyBackupMode)

/**
 * Parse and classify legacy plaintext JSON only. No normalization, validation, restoration,
 * persistence or safety backup is performed here. Inputs should be bounded while streaming
 * before allocating this byte array; this entry point cannot prevent prior allocation.
 */
object LegacyBackupReader {
    const val MAX_BYTES = 64 * 1024 * 1024
    private const val MAX_DEPTH = 128
    private const val MAX_NODES = 1_000_000
    private const val MAX_NUMBER_CHARS = 128

    fun read(bytes: ByteArray, mode: LegacyBackupMode): LegacyBackupDocument {
        if (bytes.size > MAX_BYTES) throw BackupReadException(BackupReadException.Reason.SIZE_LIMIT)
        val text = try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw BackupReadException(BackupReadException.Reason.INVALID_UTF8)
        }
        // File.text() in the original browser strips a UTF-8 BOM.
        val root = Parser(text.removePrefix("\uFEFF")).parse() as? JsonValue.Object
            ?: throw BackupReadException(BackupReadException.Reason.INVALID_SHAPE)
        val fields = root.fields
        val envelope = fields.containsKey("app") || fields.containsKey("kind")
        if (envelope) {
            val version = fields["version"] as? JsonValue.Number
            if (fields["app"] != JsonValue.Text("stayar") ||
                fields["kind"] != JsonValue.Text(if (mode == LegacyBackupMode.FULL) "full" else "project") ||
                version?.raw != "2") throw BackupReadException(BackupReadException.Reason.UNSUPPORTED_ENVELOPE)
        }
        if (envelope && !fields.containsKey(if (mode == LegacyBackupMode.FULL) "state" else "project"))
            throw BackupReadException(BackupReadException.Reason.INVALID_SHAPE)
        val candidate = when (mode) {
            LegacyBackupMode.FULL -> fields["state"] ?: root
            LegacyBackupMode.PROJECT -> fields["project"] ?: (fields["projects"] as? JsonValue.Array)?.values?.firstOrNull() ?: root
        }
        val payload = candidate as? JsonValue.Object ?: throw BackupReadException(BackupReadException.Reason.INVALID_SHAPE)
        val required = if (mode == LegacyBackupMode.FULL) "projects" else "users"
        if (payload.fields[required] !is JsonValue.Array) throw BackupReadException(BackupReadException.Reason.INVALID_SHAPE)
        return LegacyBackupDocument(root, payload, mode)
    }

    private class Parser(private val text: String) {
        private var pos = 0
        private var nodes = 0
        private fun invalid(): Nothing = throw BackupReadException(BackupReadException.Reason.INVALID_JSON)
        private fun limit(): Nothing = throw BackupReadException(BackupReadException.Reason.RESOURCE_LIMIT)
        private fun whitespace() { while (pos < text.length && text[pos] in " \t\n\r") pos++ }
        private fun take(c: Char): Boolean { whitespace(); if (pos < text.length && text[pos] == c) { pos++; return true }; return false }
        fun parse(): JsonValue { val result = value(0); whitespace(); if (pos != text.length) invalid(); return result }
        private fun value(depth: Int): JsonValue {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES) limit()
            whitespace(); if (pos >= text.length) invalid()
            return when (text[pos]) {
                '{' -> objectValue(depth)
                '[' -> arrayValue(depth)
                '"' -> JsonValue.Text(string())
                't' -> { keyword("true"); JsonValue.Bool(true) }
                'f' -> { keyword("false"); JsonValue.Bool(false) }
                'n' -> { keyword("null"); JsonValue.Null }
                '-', in '0'..'9' -> number()
                else -> invalid()
            }
        }
        private fun keyword(word: String) { if (!text.startsWith(word, pos)) invalid(); pos += word.length }
        private fun objectValue(depth: Int): JsonValue.Object {
            pos++; val values = LinkedHashMap<String, JsonValue>()
            if (take('}')) return JsonValue.Object(Collections.unmodifiableMap(values))
            do {
                whitespace(); if (pos >= text.length || text[pos] != '"') invalid()
                val key = string(); if (!take(':') || values.containsKey(key)) invalid()
                values[key] = value(depth + 1)
            } while (take(','))
            if (!take('}')) invalid()
            return JsonValue.Object(Collections.unmodifiableMap(values))
        }
        private fun arrayValue(depth: Int): JsonValue.Array {
            pos++; val values = ArrayList<JsonValue>()
            if (take(']')) return JsonValue.Array(Collections.unmodifiableList(values))
            do { values.add(value(depth + 1)) } while (take(','))
            if (!take(']')) invalid()
            return JsonValue.Array(Collections.unmodifiableList(values))
        }
        private fun string(): String {
            if (text[pos++] != '"') invalid()
            val out = StringBuilder()
            while (pos < text.length) {
                val c = text[pos++]
                if (c == '"') {
                    val str = out.toString()
                    var i = 0
                    while (i < str.length) {
                        val ch = str[i]
                        if (Character.isHighSurrogate(ch)) {
                            if (i + 1 >= str.length || !Character.isLowSurrogate(str[i + 1])) invalid()
                            i += 2
                        } else { if (Character.isLowSurrogate(ch)) invalid(); i++ }
                    }
                    return str
                }
                if (c == '\\') {
                    if (pos >= text.length) invalid()
                    when (text[pos++]) {
                        '"' -> out.append('"'); '\\' -> out.append('\\'); '/' -> out.append('/')
                        'b' -> out.append('\b'); 'f' -> out.append('\u000c'); 'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) invalid()
                            val hex = text.substring(pos, pos + 4)
                            if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) invalid()
                            out.append(hex.toInt(16).toChar()); pos += 4
                        }
                        else -> invalid()
                    }
                } else { if (c.code < 32) invalid(); out.append(c) }
            }
            invalid()
        }
        private fun number(): JsonValue.Number {
            val start = pos
            if (text[pos] == '-') pos++
            if (pos >= text.length) invalid()
            if (text[pos] == '0') pos++ else {
                if (text[pos] !in '1'..'9') invalid()
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && text[pos] == '.') {
                pos++; val first = pos; while (pos < text.length && text[pos] in '0'..'9') pos++
                if (pos == first) invalid()
            }
            if (pos < text.length && text[pos] in "eE") {
                pos++; if (pos < text.length && text[pos] in "+-") pos++
                val first = pos; while (pos < text.length && text[pos] in '0'..'9') pos++
                if (pos == first) invalid()
            }
            if (pos - start > MAX_NUMBER_CHARS) limit()
            return JsonValue.Number(text.substring(start, pos))
        }
    }
}
