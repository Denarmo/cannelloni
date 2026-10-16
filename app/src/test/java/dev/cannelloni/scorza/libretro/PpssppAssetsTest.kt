package dev.cannelloni.scorza.libretro

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PpssppAssetsTest {

    private lateinit var systemDir: File
    private val ppssppDir get() = File(systemDir, "PPSSPP")

    @Before fun setUp() {
        systemDir = Files.createTempDirectory("ppsspp-assets").toFile()
    }

    @After fun tearDown() {
        systemDir.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String?>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                if (content != null) out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun upstreamZip(atlas: String = "atlas-v1") = zip(
        "PPSSPP/" to null,
        "PPSSPP/ppge_atlas.zim" to atlas,
        "PPSSPP/flash0/font/jpn0.pgf" to "font",
        "PPSSPP/lang/en_US.ini" to "lang",
    )

    @Test fun `extracts upstream layout into the PPSSPP folder`() {
        assertTrue(PpssppAssets.installFromZip(upstreamZip(), systemDir, "v1"))
        assertEquals("atlas-v1", File(ppssppDir, "ppge_atlas.zim").readText())
        assertEquals("font", File(ppssppDir, "flash0/font/jpn0.pgf").readText())
        assertTrue(File(ppssppDir, "lang/en_US.ini").isFile)
        assertFalse("no nested PPSSPP folder", File(ppssppDir, "PPSSPP").exists())
        assertTrue(PpssppAssets.hasAssets(systemDir))
        assertTrue(PpssppAssets.isUpToDate(systemDir, "v1"))
        assertFalse(PpssppAssets.isUpToDate(systemDir, "v2"))
    }

    @Test fun `extracts a zip rooted at the asset folder`() {
        val z = zip("ppge_atlas.zim" to "a", "flash0/font/ltn0.pgf" to "f")
        assertTrue(PpssppAssets.installFromZip(z, systemDir, "v1"))
        assertTrue(File(ppssppDir, "flash0/font/ltn0.pgf").isFile)
    }

    @Test fun `leaves no temp folder behind`() {
        PpssppAssets.installFromZip(upstreamZip(), systemDir, "v1")
        assertEquals(listOf("PPSSPP"), systemDir.list()!!.toList())
    }

    @Test fun `upgrade replaces asset files and keeps user files`() {
        PpssppAssets.installFromZip(upstreamZip(), systemDir, "v1")
        File(ppssppDir, "TEXTURES/ULUS12345").mkdirs()
        File(ppssppDir, "TEXTURES/ULUS12345/textures.ini").writeText("user")
        File(ppssppDir, "Cheats").mkdirs()
        File(ppssppDir, "Cheats/cheat.db").writeText("cheats")

        assertTrue(PpssppAssets.installFromZip(upstreamZip(atlas = "atlas-v2"), systemDir, "v2"))

        assertEquals("atlas-v2", File(ppssppDir, "ppge_atlas.zim").readText())
        assertEquals("user", File(ppssppDir, "TEXTURES/ULUS12345/textures.ini").readText())
        assertEquals("cheats", File(ppssppDir, "Cheats/cheat.db").readText())
        assertTrue(PpssppAssets.isUpToDate(systemDir, "v2"))
    }

    @Test fun `upgrade removes files the previous bundle installed and the new one dropped`() {
        PpssppAssets.installFromZip(upstreamZip(), systemDir, "v1")
        File(ppssppDir, "mine.txt").writeText("keep")
        val v2 = zip("PPSSPP/ppge_atlas.zim" to "atlas-v2", "PPSSPP/flash0/font/jpn0.pgf" to "font")
        PpssppAssets.installFromZip(v2, systemDir, "v2")
        assertFalse(File(ppssppDir, "lang/en_US.ini").exists())
        assertTrue(File(ppssppDir, "mine.txt").isFile)
    }

    @Test fun `a deleted asset file makes the install stale`() {
        PpssppAssets.installFromZip(upstreamZip(), systemDir, "v1")
        File(ppssppDir, "flash0/font/jpn0.pgf").delete()
        assertFalse(PpssppAssets.isUpToDate(systemDir, "v1"))
    }

    @Test fun `zip slip entries are rejected and nothing is installed`() {
        val evil = zip("PPSSPP/ppge_atlas.zim" to "a", "PPSSPP/../../evil.txt" to "x")
        try {
            PpssppAssets.installFromZip(evil, systemDir, "v1")
            fail("expected IOException")
        } catch (_: IOException) {
        }
        assertFalse(File(systemDir.parentFile, "evil.txt").exists())
        assertFalse(ppssppDir.exists())
        assertTrue("temp folder cleaned up", systemDir.list()!!.isEmpty())
    }

    @Test fun `a corrupt zip keeps the existing install`() {
        PpssppAssets.installFromZip(upstreamZip(), systemDir, "v1")
        val good = upstreamZip(atlas = "atlas-v2").readBytes()
        val truncated = ByteArrayInputStream(good.copyOf(good.size / 2))
        try {
            PpssppAssets.installFromZip(truncated, systemDir, "v2")
        } catch (_: IOException) {
        }
        assertEquals("atlas-v1", File(ppssppDir, "ppge_atlas.zim").readText())
        assertTrue(PpssppAssets.isUpToDate(systemDir, "v1"))
    }

    @Test fun `relative names strip the PPSSPP root and reject traversal`() {
        assertEquals("flash0/font/a.pgf", PpssppAssets.relativeName("PPSSPP/flash0/font/a.pgf"))
        assertEquals("flash0/a.pgf", PpssppAssets.relativeName("flash0\\a.pgf"))
        assertNull(PpssppAssets.relativeName("PPSSPP/"))
        for (bad in listOf("/etc/passwd", "../x", "PPSSPP/../../x", "C:/x")) {
            try {
                PpssppAssets.relativeName(bad)
                fail("accepted $bad")
            } catch (_: IOException) {
            }
        }
    }

    @Test fun `ppsspp core ids are recognised`() {
        assertTrue(PpssppAssets.isPpssppCore("ppsspp_libretro"))
        assertTrue(PpssppAssets.isPpssppCore("/data/cores/ppsspp_libretro_android.so"))
        assertFalse(PpssppAssets.isPpssppCore("melonds_libretro"))
    }
}
