package io.github.axiomvoidloop.takdisannurat.data

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MiniJsonTest {

    private fun assertRejected(text: String) {
        try {
            MiniJson.parse(text)
        } catch (e: MiniJson.ParseException) {
            return
        }
        fail("should have been rejected: $text")
    }

    @Test
    fun readsSimpleValues() {
        assertEquals(true, MiniJson.parse("true"))
        assertEquals(false, MiniJson.parse(" false "))
        assertEquals(null, MiniJson.parse("null"))
        assertEquals(42L, MiniJson.parse("42"))
        assertEquals(-7L, MiniJson.parse("-7"))
        assertEquals(0L, MiniJson.parse("0"))
        assertEquals(2.5, MiniJson.parse("2.5"))
        assertEquals(1000.0, MiniJson.parse("1e3"))
        assertEquals("hello", MiniJson.parse("\"hello\""))
    }

    @Test
    fun readsNestedStructures() {
        val parsed = MiniJson.parse("""{"a": [1, 2, {"b": null}], "c": {"d": "x"}}""")
        val expected = mapOf(
            "a" to listOf(1L, 2L, mapOf("b" to null)),
            "c" to mapOf("d" to "x"),
        )
        assertEquals(expected, parsed)
    }

    @Test
    fun objectsKeepTheirKeyOrder() {
        val parsed = MiniJson.parse("""{"z": 1, "a": 2, "m": 3}""") as Map<*, *>
        assertEquals(listOf("z", "a", "m"), parsed.keys.toList())
    }

    @Test
    fun readsEscapesAndUnicode() {
        assertEquals("a\"b\\c/d\ne\tf", MiniJson.parse("\"a\\\"b\\\\c\\/d\\ne\\tf\""))
        assertEquals("A", MiniJson.parse("\"\\u0041\""))
        assertEquals("\uD83D\uDE00", MiniJson.parse("\"\\uD83D\\uDE00\""))
        assertEquals("নূর", MiniJson.parse("\"নূর\""))
    }

    @Test
    fun ignoresAByteOrderMark() {
        assertEquals(1L, MiniJson.parse("\uFEFF1"))
    }

    @Test
    fun rejectsBrokenDocuments() {
        assertRejected("")
        assertRejected("   ")
        assertRejected("{")
        assertRejected("[1, 2")
        assertRejected("[1, 2,]")
        assertRejected("{\"a\": 1,}")
        assertRejected("{\"a\" 1}")
        assertRejected("{a: 1}")
        assertRejected("'text'")
        assertRejected("\"not closed")
        assertRejected("[1] [2]")
        assertRejected("1 2")
        assertRejected("nul")
        assertRejected("truee")
        assertRejected("// comment\n1")
    }

    @Test
    fun rejectsBadNumbers() {
        assertRejected("01")
        assertRejected("-")
        assertRejected("1.")
        assertRejected(".5")
        assertRejected("+1")
        assertRejected("1e")
        assertRejected("NaN")
        assertRejected("Infinity")
        assertRejected("1e999")
    }

    @Test
    fun rejectsBadStrings() {
        assertRejected("\"a\\xb\"")
        assertRejected("\"\\u12\"")
        assertRejected("\"\\uZZZZ\"")
        assertRejected("\"line\nbreak\"")
        assertRejected("\"tab\there\"")
    }

    @Test
    fun rejectsUnpairedSurrogates() {
        assertRejected("\"\\ud83d\"")
        assertRejected("\"\\ude00\"")
        assertRejected("\"\\ude00\\ud83d\"")
    }

    @Test
    fun hugeWholeNumbersAreNotTreatedAsWholeNumbers() {
        // Too big for a Long, so they come back as Double and the backup reader refuses them as counts.
        assertEquals(1.0E20, MiniJson.parse("100000000000000000000"))
    }

    @Test
    fun rejectsDuplicateKeys() {
        assertRejected("""{"a": 1, "a": 2}""")
    }

    @Test
    fun rejectsDeepNesting() {
        val deep = "[".repeat(MiniJson.MAX_DEPTH + 5) + "]".repeat(MiniJson.MAX_DEPTH + 5)
        assertRejected(deep)
        val fine = "[".repeat(10) + "]".repeat(10)
        assertTrue(MiniJson.parse(fine) is List<*>)
    }

    @Test
    fun writesCompactJson() {
        val value = linkedMapOf("a" to 1, "b" to listOf(true, null, "x"), "c" to linkedMapOf<String, Any?>())
        assertEquals("""{"a":1,"b":[true,null,"x"],"c":{}}""", MiniJson.stringify(value, pretty = false))
    }

    @Test
    fun writesPrettyJson() {
        val value = linkedMapOf("a" to 1, "b" to listOf(1, 2))
        val expected = "{\n  \"a\": 1,\n  \"b\": [\n    1,\n    2\n  ]\n}"
        assertEquals(expected, MiniJson.stringify(value))
    }

    @Test
    fun escapesWhatNeedsEscaping() {
        val text = "quote\" backslash\\ newline\n tab\t bell\u0007 end"
        val json = MiniJson.stringify(text, pretty = false)
        assertEquals("\"quote\\\" backslash\\\\ newline\\n tab\\t bell\\u0007 end\"", json)
        assertEquals(text, MiniJson.parse(json))
    }

    @Test
    fun whatIsWrittenCanBeReadBack() {
        val value = linkedMapOf<String, Any?>(
            "name" to "নূর \uD83D\uDE00 \"quoted\"",
            "numbers" to listOf(0L, -1L, 123456789012L, 1.5),
            "flags" to listOf(true, false, null),
            "nested" to linkedMapOf<String, Any?>("empty" to emptyList<Any?>(), "obj" to emptyMap<String, Any?>()),
        )
        assertEquals(value, MiniJson.parse(MiniJson.stringify(value)))
        assertEquals(value, MiniJson.parse(MiniJson.stringify(value, pretty = false)))
    }
}
