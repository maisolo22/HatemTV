package com.nuvio.tv.ui.screens.livetv

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class LiveTVMenuOrderTest {
    private val keys = listOf("channels", "folder:sport", "source:fr", "source:it")
    @Test fun `moving a folder among addon categories preserves every unique entry`() {
        val moved = moveTVMenuKey(keys, "folder:sport", 1)
        assertEquals(listOf("channels", "source:fr", "folder:sport", "source:it"), moved)
        assertEquals(keys.toSet(), moved.toSet())
        assertEquals(moved, moveTVMenuKey(moved, "missing", 1))
        assertEquals(keys, moveTVMenuKey(keys, "channels", -1))
        assertEquals(keys, moveTVMenuKey(keys, "source:it", 1))
    }
    @Test fun `new categories are appended without losing saved placement and missing addons are hidden`() {
        val saved = listOf("source:it", "folder:sport", "source:disabled", "channels", "source:fr")
        assertEquals(listOf("source:it", "folder:sport", "channels", "source:fr", "folder:films"),
            orderedTVMenuKeys(saved, keys + "folder:films"))
        assertEquals("source:disabled", orderedTVMenuKeys(saved, keys + "source:disabled")[2])
    }
    @Test fun `draft movement does not modify library until confirmation and order survives rename and JSON`() {
        val library = LiveTVLibrary().createFolder("Sport", id = "sport").withMenuOrder(keys)
        val draft = moveTVMenuKey(library.menuOrder, "folder:sport", 1)
        assertEquals(keys, library.menuOrder)
        val committed = library.withMenuOrder(draft).renameFolder("sport", "Sports")
        val restored = Gson().fromJson(Gson().toJson(committed), LiveTVLibrary::class.java)
        assertEquals(draft, restored.menuOrder)
        assertEquals("Sports", restored.folders.single().name)
        assertFalse(restored.deleteFolder("sport").menuOrder.contains("folder:sport"))
        assertTrue(Gson().fromJson("{}", LiveTVLibrary::class.java).menuOrder.isEmpty())
    }
}
