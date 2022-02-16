package dev.lowrespalmtree.comet

import dev.lowrespalmtree.comet.utils.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Test utils.Uri functions. Runs of the device due to the Uri functions being Android-only .*/
class UriUtilsTest {
    @Test
    fun resolveLinkUri() {
        // Absolute URLs.
        assertEquals(
            "gemini://example.com/",
            resolveLinkUri("gemini://example.com", "gemini://dece.space/").toString()
        )
        // Relative links.
        assertEquals(
            "gemini://example.com/",
            resolveLinkUri(".", "gemini://example.com/").toString()
        )
        assertEquals(
            "gemini://example.com/",
            resolveLinkUri("..", "gemini://example.com/").toString()
        )
        assertEquals(
            "gemini://example.com/page",
            resolveLinkUri("./page", "gemini://example.com/").toString()
        )
        assertEquals(
            "gemini://example.com/page",
            resolveLinkUri("page", "gemini://example.com/").toString()
        )
        assertEquals(
            "gemini://example.com/page.com",
            resolveLinkUri("page.com", "gemini://example.com/").toString()
        )
        // Scheme-less URLs.
        assertEquals(
            "gemini://someone.smol.pub/somepage",
            resolveLinkUri("//someone.smol.pub/somepage", "gemini://smol.pub/feed").toString()
        )
    }

    @Test
    fun joinUrls() {
        assertEquals(
            "gemini://dece.space/some-file.gmi",
            joinUrls("gemini://dece.space/", "some-file.gmi").toString()
        )
        assertEquals(
            "gemini://dece.space/some-file.gmi",
            joinUrls("gemini://dece.space/", "./some-file.gmi").toString()
        )
        assertEquals(
            "gemini://dece.space/some-file.gmi",
            joinUrls("gemini://dece.space/dir1", "/some-file.gmi").toString()
        )
        assertEquals(
            "gemini://dece.space/dir1/other-file.gmi",
            joinUrls("gemini://dece.space/dir1/file.gmi", "other-file.gmi").toString()
        )
        assertEquals(
            "gemini://dece.space/top-level.gmi",
            joinUrls("gemini://dece.space/dir1/file.gmi", "../top-level.gmi").toString()
        )
        assertEquals(
            "s://hard/test/b/d/a.gmi",
            joinUrls("s://hard/dir/a", "./../test/b/c/../d/e/f/../.././a.gmi").toString()
        )
    }

    @Test
    fun removeDotSegments() {
        arrayOf(
            Pair("index.gmi", "index.gmi"),
            Pair("/index.gmi", "/index.gmi"),
            Pair("./index.gmi", "index.gmi"),
            Pair("/./index.gmi", "/index.gmi"),
            Pair("/../index.gmi", "/index.gmi"),
            Pair("/a/b/c/./../../g", "/a/g"),
            Pair("mid/content=5/../6", "mid/6"),
            Pair("../../../../g", "g")
        ).forEach { (path, expected) ->
            assertEquals(expected, removeDotSegments(path))
        }
    }

    @Test
    fun removeLastSegment() {
        assertEquals("", removeLastSegment(""))
        assertEquals("", removeLastSegment("/"))
        assertEquals("", removeLastSegment("/a"))
        assertEquals("/a", removeLastSegment("/a/"))
        assertEquals("/a", removeLastSegment("/a/b"))
        assertEquals("/a/b/c", removeLastSegment("/a/b/c/d"))
        assertEquals("//", removeLastSegment("///"))
    }

    @Test
    fun popFirstSegment() {
        assertEquals(Pair("", ""), popFirstSegment(""))
        assertEquals(Pair("a", ""), popFirstSegment("a"))
        assertEquals(Pair("/a", ""), popFirstSegment("/a"))
        assertEquals(Pair("/a", "/"), popFirstSegment("/a/"))
        assertEquals(Pair("/a", "/b"), popFirstSegment("/a/b"))
        assertEquals(Pair("a", "/b"), popFirstSegment("a/b"))
    }
}