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
            dev.lowrespalmtree.comet.utils.joinUrls("gemini://dece.space/", "some-file.gmi")
                .toString()
        )
        assertEquals(
            "gemini://dece.space/some-file.gmi",
            dev.lowrespalmtree.comet.utils.joinUrls("gemini://dece.space/", "./some-file.gmi")
                .toString()
        )
        assertEquals(
            "gemini://dece.space/some-file.gmi",
            dev.lowrespalmtree.comet.utils.joinUrls("gemini://dece.space/dir1", "/some-file.gmi")
                .toString()
        )
        assertEquals(
            "gemini://dece.space/dir1/other-file.gmi",
            dev.lowrespalmtree.comet.utils.joinUrls(
                "gemini://dece.space/dir1/file.gmi",
                "other-file.gmi"
            ).toString()
        )
        assertEquals(
            "gemini://dece.space/top-level.gmi",
            dev.lowrespalmtree.comet.utils.joinUrls(
                "gemini://dece.space/dir1/file.gmi",
                "../top-level.gmi"
            ).toString()
        )
        assertEquals(
            "s://hard/test/b/d/a.gmi",
            dev.lowrespalmtree.comet.utils.joinUrls(
                "s://hard/dir/a",
                "./../test/b/c/../d/e/f/../.././a.gmi"
            ).toString()
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
            assertEquals(expected, dev.lowrespalmtree.comet.utils.removeDotSegments(path))
        }
    }

    @Test
    fun removeLastSegment() {
        assertEquals("", dev.lowrespalmtree.comet.utils.removeLastSegment(""))
        assertEquals("", dev.lowrespalmtree.comet.utils.removeLastSegment("/"))
        assertEquals("", dev.lowrespalmtree.comet.utils.removeLastSegment("/a"))
        assertEquals("/a", dev.lowrespalmtree.comet.utils.removeLastSegment("/a/"))
        assertEquals("/a", dev.lowrespalmtree.comet.utils.removeLastSegment("/a/b"))
        assertEquals("/a/b/c", dev.lowrespalmtree.comet.utils.removeLastSegment("/a/b/c/d"))
        assertEquals("//", dev.lowrespalmtree.comet.utils.removeLastSegment("///"))
    }

    @Test
    fun popFirstSegment() {
        assertEquals(Pair("", ""), dev.lowrespalmtree.comet.utils.popFirstSegment(""))
        assertEquals(Pair("a", ""), dev.lowrespalmtree.comet.utils.popFirstSegment("a"))
        assertEquals(Pair("/a", ""), dev.lowrespalmtree.comet.utils.popFirstSegment("/a"))
        assertEquals(Pair("/a", "/"), dev.lowrespalmtree.comet.utils.popFirstSegment("/a/"))
        assertEquals(Pair("/a", "/b"), dev.lowrespalmtree.comet.utils.popFirstSegment("/a/b"))
        assertEquals(Pair("a", "/b"), dev.lowrespalmtree.comet.utils.popFirstSegment("a/b"))
    }
}