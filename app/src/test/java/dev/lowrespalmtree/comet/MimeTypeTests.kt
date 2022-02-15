package dev.lowrespalmtree.comet

import org.junit.Assert.*
import org.junit.Test

class MimeTypeTests {
    @Test
    fun from() {
        assertNull(MimeType.from(""))
        assertNull(MimeType.from("dumb"))
        assertNull(MimeType.from("dumb;dumber"))
        assertNull(MimeType.from("123456"))

        MimeType.from("a/b")?.run {
            assertEquals("a", main)
            assertEquals("b", sub)
            assertEquals(mapOf<String, String>(), params)
        } ?: fail()

        MimeType.from("text/gemini")?.run {
            assertEquals("text", main)
            assertEquals("gemini", sub)
            assertEquals(mapOf<String, String>(), params)
        } ?: fail()

        MimeType.from("text/gemini;lang=en")?.run {
            assertEquals("text", main)
            assertEquals("gemini", sub)
            assertEquals(mapOf("lang" to "en"), params)
        } ?: fail()

        MimeType.from("text/gemini ;lang=en")?.run {
            assertEquals("text", main)
            assertEquals("gemini", sub)
            assertEquals(mapOf("lang" to "en"), params)
        } ?: fail()
    }
}
