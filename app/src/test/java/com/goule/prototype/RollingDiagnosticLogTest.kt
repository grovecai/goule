package com.goule.prototype

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RollingDiagnosticLogTest {
    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("goule-diagnostics-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun retainsMoreThan32EventsAcrossReopenAndCapsTotalDiskUsage() = withDirectory { directory ->
        val log = RollingDiagnosticLog(directory, segmentBytes = 1024, segments = 4)
        repeat(100) { assertTrue(log.append("event=$it target=fixture")) }
        val restored = RollingDiagnosticLog(directory, 1024, 4).snapshot()
        assertTrue(restored.contains("event=0 "))
        assertTrue(restored.contains("event=99 "))
        repeat(1000) { assertTrue(log.append("next=$it target=fixture")) }
        val recent = RollingDiagnosticLog(directory, 1024, 4).snapshot()
        assertFalse(recent.contains("event=0 "))
        assertTrue(recent.contains("next=999 "))
        val entries = Regex("next=(\\d+)").findAll(recent).map { it.groupValues[1].toInt() }.toList()
        assertEquals(entries.sorted(), entries)
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 4096)
        assertTrue(directory.listFiles()!!.size <= 4)
    }

    @Test fun malformedHugeAndMultilineRecordsCannotEscapeTheSizeLimit() = withDirectory { directory ->
        val log = RollingDiagnosticLog(directory, 256, 2)
        repeat(20) { assertTrue(log.append("中".repeat(1000) + "\nforged record\r")) }
        assertTrue(directory.listFiles()!!.all { it.length() <= 256 })
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 512)
        assertTrue(log.append("event one\nevent two"))
        assertTrue(log.snapshot().contains("event one event two\n"))
    }

    @Test fun inaccessibleStorageDoesNotThrowAndExplainsMissingRecords() = withDirectory { directory ->
        val notDirectory = File(directory, "file").apply { writeText("occupied") }
        val log = RollingDiagnosticLog(notDirectory)
        assertFalse(log.append("timing must continue"))
        assertEquals(1L, log.failures)
        assertEquals("IOException", log.lastError)
        assertTrue(log.snapshot().contains("failures=1"))
    }
}
