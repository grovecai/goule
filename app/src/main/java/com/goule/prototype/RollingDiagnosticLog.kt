package com.goule.prototype

import java.io.File
import java.io.IOException

/** Bounded local journal. Its owner serializes access on a worker, away from timing callbacks. */
internal class RollingDiagnosticLog(
    private val directory: File,
    private val segmentBytes: Int = 256 * 1024,
    private val segments: Int = 4,
) {
    var failures = 0L
        private set
    var lastError: String? = null
        private set

    init {
        require(segmentBytes >= 256 && segments in 1..16)
    }

    fun append(line: String): Boolean = try {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create journal directory")
        // Keep each record single-line and bounded even if unexpected metadata reaches the logger.
        val bytes = (line.replace('\n', ' ').replace('\r', ' ').take(segmentBytes / 4 - 1) + "\n")
            .toByteArray(Charsets.UTF_8)
        val current = file(0)
        if (current.length() + bytes.size > segmentBytes) {
            for (index in segments - 1 downTo 0) {
                val source = file(index)
                if (!source.exists()) continue
                if (index == segments - 1) {
                    if (!source.delete()) throw IOException("Cannot remove journal segment")
                } else if (!source.renameTo(file(index + 1))) throw IOException("Cannot rotate journal segment")
            }
        }
        current.appendBytes(bytes)
        true
    } catch (error: IOException) {
        failed(error)
        false
    } catch (error: SecurityException) {
        failed(error)
        false
    }

    fun snapshot(): String = try {
        buildString {
            appendLine("journal failures=$failures lastError=$lastError maxBytes=${segmentBytes * segments}")
            for (index in segments - 1 downTo 0) {
                val segment = file(index)
                // Bound reads too, including malformed or unexpectedly oversized existing files.
                if (segment.isFile) segment.inputStream().use {
                    val buffer = ByteArray(segmentBytes)
                    var count = 0
                    while (count < buffer.size) {
                        val read = it.read(buffer, count, buffer.size - count)
                        if (read <= 0) break
                        count += read
                    }
                    append(String(buffer, 0, count, Charsets.UTF_8))
                }
            }
        }
    } catch (error: IOException) {
        failed(error)
        "journal unavailable failures=$failures lastError=$lastError\n"
    } catch (error: SecurityException) {
        failed(error)
        "journal unavailable failures=$failures lastError=$lastError\n"
    }

    private fun failed(error: Exception) {
        failures++
        lastError = error.javaClass.simpleName
    }

    private fun file(index: Int) = File(directory, "timing.$index.log")
}
