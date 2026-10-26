package io.github.axiomvoidloop.takdisannurat.data

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

/**
 * A small, strict JSON reader and writer with no dependencies.
 *
 * Reads: objects become [Map] (keys keep their order), arrays become [List], strings become [String],
 * whole numbers become [Long], other numbers become [Double], plus [Boolean] and null.
 *
 * It is strict on purpose, because it reads files the user imports: no trailing commas, no comments,
 * no duplicate keys, no leading zeros, no raw control characters inside strings, and nesting
 * is limited to [MAX_DEPTH] levels.
 */
object MiniJson {

    class ParseException(message: String) : Exception(message)

    const val MAX_DEPTH = 32

    fun parse(text: String): Any? = Parser(text.removePrefix("\uFEFF")).parseDocument()

    fun stringify(value: Any?, pretty: Boolean = true): String {
        val out = StringBuilder()
        write(out, value, pretty, 0)
        return out.toString()
    }

    // ---- reading ----

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Any? {
            skipWhitespace()
            val value = parseValue(0)
            skipWhitespace()
            if (i != s.length) fail("unexpected content after the end of the document")
            return value
        }

        private fun fail(message: String): Nothing = throw ParseException("$message (at position $i)")

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun skipWhitespace() {
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++ else break
            }
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("nesting is too deep")
            if (i >= s.length) fail("unexpected end of the document")
            val c = s[i]
            return when {
                c == '{' -> parseObject(depth)
                c == '[' -> parseArray(depth)
                c == '"' -> parseString()
                c == 't' -> literal("true", true)
                c == 'f' -> literal("false", false)
                c == 'n' -> literal("null", null)
                c == '-' || c in '0'..'9' -> parseNumber()
                else -> fail("unexpected character '$c'")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("unexpected text")
            i += word.length
            return value
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            i++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("expected a text key")
                val key = parseString()
                skipWhitespace()
                if (peek() != ':') fail("expected ':'")
                i++
                skipWhitespace()
                val value = parseValue(depth + 1)
                if (map.containsKey(key)) fail("duplicate key \"$key\"")
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return map
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            i++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(parseValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return list
                    }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("the text is not closed")
                val c = s[i++]
                when {
                    c == '"' -> return checkedText(sb.toString())
                    c == '\\' -> {
                        if (i >= s.length) fail("the text is not closed")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("incomplete \\u escape")
                                var code = 0
                                for (k in 0 until 4) {
                                    val digit = Character.digit(s[i + k], 16)
                                    if (digit < 0) fail("invalid \\u escape")
                                    code = code * 16 + digit
                                }
                                i += 4
                                sb.append(code.toChar())
                            }
                            else -> fail("invalid escape '\\$e'")
                        }
                    }
                    c < ' ' -> fail("control character inside text")
                    else -> sb.append(c)
                }
            }
        }

        /** A lone surrogate cannot be saved as UTF-8 without being changed, so it is refused. */
        private fun checkedText(text: String): String {
            var k = 0
            while (k < text.length) {
                val c = text[k]
                if (Character.isHighSurrogate(c)) {
                    if (k + 1 >= text.length || !Character.isLowSurrogate(text[k + 1])) fail("invalid unicode in text")
                    k += 2
                } else if (Character.isLowSurrogate(c)) {
                    fail("invalid unicode in text")
                } else {
                    k++
                }
            }
            return text
        }

        private fun parseNumber(): Any {
            val start = i
            if (peek() == '-') i++
            if (peek() == '0') {
                i++
                if (peek() in '0'..'9') fail("leading zeros are not allowed")
            } else if (peek() in '1'..'9') {
                while (peek() in '0'..'9') i++
            } else {
                fail("invalid number")
            }
            var isWhole = true
            if (peek() == '.') {
                isWhole = false
                i++
                if (peek() !in '0'..'9') fail("invalid number")
                while (peek() in '0'..'9') i++
            }
            if (peek() == 'e' || peek() == 'E') {
                isWhole = false
                i++
                if (peek() == '+' || peek() == '-') i++
                if (peek() !in '0'..'9') fail("invalid number")
                while (peek() in '0'..'9') i++
            }
            val text = s.substring(start, i)
            if (isWhole) {
                text.toLongOrNull()?.let { return it }
            }
            val d = text.toDouble()
            if (d.isInfinite() || d.isNaN()) fail("number is out of range")
            return d
        }
    }

    // ---- writing ----

    private fun write(out: StringBuilder, value: Any?, pretty: Boolean, level: Int) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value.toString())
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Double, is Float -> {
                val d = (value as Number).toDouble()
                require(d.isFinite()) { "JSON cannot hold NaN or infinity" }
                out.append(d.toString())
            }
            is String -> writeString(out, value)
            is Map<*, *> -> writeObject(out, value, pretty, level)
            is List<*> -> writeArray(out, value, pretty, level)
            else -> throw IllegalArgumentException("unsupported type: ${value::class}")
        }
    }

    private fun writeObject(out: StringBuilder, map: Map<*, *>, pretty: Boolean, level: Int) {
        if (map.isEmpty()) {
            out.append("{}")
            return
        }
        out.append('{')
        var first = true
        for ((key, v) in map) {
            require(key is String) { "object keys must be text" }
            if (!first) out.append(',')
            first = false
            if (pretty) newline(out, level + 1)
            writeString(out, key)
            out.append(if (pretty) ": " else ":")
            write(out, v, pretty, level + 1)
        }
        if (pretty) newline(out, level)
        out.append('}')
    }

    private fun writeArray(out: StringBuilder, list: List<*>, pretty: Boolean, level: Int) {
        if (list.isEmpty()) {
            out.append("[]")
            return
        }
        out.append('[')
        var first = true
        for (v in list) {
            if (!first) out.append(',')
            first = false
            if (pretty) newline(out, level + 1)
            write(out, v, pretty, level + 1)
        }
        if (pretty) newline(out, level)
        out.append(']')
    }

    private fun newline(out: StringBuilder, level: Int) {
        out.append('\n')
        repeat(level) { out.append("  ") }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c < ' ' -> out.append("\\u").append(String.format("%04x", c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }
}
