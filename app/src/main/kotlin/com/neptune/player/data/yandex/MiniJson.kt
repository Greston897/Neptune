package com.neptune.player.data.yandex

/**
 * A 120-line JSON reader, because the app needs to read four Yandex endpoints and nothing else.
 *
 * `org.json` is part of the Android framework, which makes it invisible to JVM unit tests: under
 * `testDebugUnitTest` every method of it throws "not mocked", so the parsing of a real Yandex
 * response could not be tested off-device at all — exactly the code that is most likely to break
 * when the other side changes a field name. A dependency-free reader keeps the whole mapping layer
 * unit-testable with fixtures captured from the real service.
 *
 * Scope: objects, arrays, strings with escapes, numbers, booleans, null. No comments, no trailing
 * commas. That is the whole of what these endpoints emit.
 */
object MiniJson {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipWhitespace()
        return value
    }

    /** Reader for objects that may sit behind an envelope the caller already unwrapped. */
    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()

    fun list(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()

    private class Reader(private val s: String) {
        private var i = 0

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWhitespace()
            if (i >= s.length) throw JsonException("unexpected end of input")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw JsonException("unexpected '$c' at $i")
            }
        }

        private fun obj(): Map<String, Any?> {
            i++ // {
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                skipWhitespace()
                val key = string()
                skipWhitespace()
                expect(':')
                out[key] = value()
                skipWhitespace()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> throw JsonException("expected ',' or '}' at $i")
                }
            }
        }

        private fun array(): List<Any?> {
            i++ // [
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out += value()
                skipWhitespace()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> throw JsonException("expected ',' or ']' at $i")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (i >= s.length) throw JsonException("unterminated string")
                when (val c = s[i++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        when (val e = s.getOrNull(i++) ?: throw JsonException("unterminated escape")) {
                            '"' -> out.append('"'); '\\' -> out.append('\\'); '/' -> out.append('/')
                            'b' -> out.append('\b'); 'f' -> out.append('\u000C'); 'n' -> out.append('\n')
                            'r' -> out.append('\r'); 't' -> out.append('\t')
                            'u' -> {
                                val hex = s.substring(i, i + 4)
                                i += 4
                                out.append(hex.toInt(16).toChar())
                            }
                            else -> throw JsonException("bad escape \\$e")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        /**
         * A number, stopping at the first character that cannot be part of one.
         *
         * The exponent's sign is only legal straight after an `e`, so `[1,2]` and `1,true` do not
         * swallow the delimiter and the following token — which is what a blanket "digits and any of
         * .eE+-" does, and it fails as "bad number" on perfectly good JSON.
         */
        private fun number(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit() || c == '.' || c == 'e' || c == 'E') { i++; continue }
                if ((c == '+' || c == '-') && (s[i - 1] == 'e' || s[i - 1] == 'E')) { i++; continue }
                break
            }
            return s.substring(start, i).toDoubleOrNull() ?: throw JsonException("bad number at $start")
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("expected '$word' at $i")
            i += word.length
            return result
        }

        private fun expect(c: Char) {
            if (s.getOrNull(i) != c) throw JsonException("expected '$c' at $i")
            i++
        }
    }

    /**
     * Minimal writer, for the one thing this app stores itself: the small catalogue of streamed
     * tracks behind the queue, so a restart does not turn the queue into a list of "unknown".
     */
    fun write(value: Any?): String = buildString { writeTo(this, value) }

    private fun writeTo(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Number -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                value.forEach { (k, v) ->
                    if (!first) out.append(',')
                    first = false
                    quote(out, k.toString())
                    out.append(':')
                    writeTo(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                value.forEach { v ->
                    if (!first) out.append(',')
                    first = false
                    writeTo(out, v)
                }
                out.append(']')
            }
            else -> quote(out, value.toString())
        }
    }

    private fun quote(out: StringBuilder, value: String) {
        out.append('"')
        value.forEach { c ->
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    class JsonException(message: String) : Exception(message)
}
