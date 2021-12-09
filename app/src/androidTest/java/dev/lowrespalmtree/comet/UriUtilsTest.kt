package dev.lowrespalmtree.comet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UriUtilsTest {
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