package com.example.cxfilemanager.data

import com.example.cxfilemanager.util.RootListParser
import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {
    @Volatile
    private var availableCache: Boolean? = null

    data class Result(val exitCode: Int, val output: String, val timedOut: Boolean)

    fun isAvailable(force: Boolean = false): Boolean {
        if (!force) availableCache?.let { return it }
        val result = run("id", timeoutMs = 30_000)
        val ok = !result.timedOut && result.exitCode == 0 && result.output.contains("uid=0")
        availableCache = ok
        return ok
    }

    fun reset() {
        availableCache = null
    }

    fun list(path: String, showHidden: Boolean, sort: SortSpec): List<FileItem> {
        val result = run("ls -la ${quote(path)}", timeoutMs = 60_000)
        if (result.timedOut || result.exitCode != 0) {
            throw SecurityException("Root cannot read $path")
        }
        val items = RootListParser.parse(result.output, path, showHidden)
        return FileOps.sort(items, sort)
    }

    fun exists(path: String): Boolean {
        val result = run("if [ -e ${quote(path)} ]; then echo 1; else echo 0; fi")
        return !result.timedOut && result.output.trim().endsWith("1")
    }

    fun mkdir(path: String): Boolean {
        val result = run("mkdir -p ${quote(path)}", timeoutMs = 30_000)
        return !result.timedOut && result.exitCode == 0
    }

    fun touch(path: String): Boolean {
        val result = run("touch ${quote(path)}", timeoutMs = 30_000)
        return !result.timedOut && result.exitCode == 0
    }

    fun delete(path: String): Boolean {
        val result = run("rm -rf ${quote(path)}", timeoutMs = 120_000)
        return !result.timedOut && result.exitCode == 0
    }

    fun rename(oldPath: String, newPath: String): Boolean {
        if (exists(newPath)) return false
        val result = run("mv ${quote(oldPath)} ${quote(newPath)}", timeoutMs = 60_000)
        return !result.timedOut && result.exitCode == 0
    }

    fun copy(src: String, dest: String, move: Boolean, destLocal: Boolean): Boolean {
        if (!move && exists(dest)) return false
        val command = buildString {
            if (move) append("mv") else append("cp -r")
            append(' ').append(quote(src)).append(' ').append(quote(dest))
            if (destLocal) {
                append(" && chmod -R a+rX ").append(quote(dest))
            }
        }
        val result = run(command, timeoutMs = 10 * 60_000)
        return !result.timedOut && result.exitCode == 0
    }

    fun copyToLocal(src: String, dest: String): Boolean {
        val result = run(
            "cp -r ${quote(src)} ${quote(dest)} && chmod -R a+rX ${quote(dest)}",
            timeoutMs = 5 * 60_000,
        )
        return !result.timedOut && result.exitCode == 0
    }

    fun copyToCache(src: String, cacheDir: File, name: String): File? {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) return null
        val dest = File(cacheDir, name.ifBlank { "preview" })
        runCatching { dest.deleteRecursively() }
        return if (copyToLocal(src, dest.absolutePath)) dest else null
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun run(command: String, timeoutMs: Long = 30_000): Result {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { input ->
                        val buffer = CharArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (output.length < MAX_OUTPUT) {
                                output.append(buffer, 0, minOf(read, MAX_OUTPUT - output.length))
                            }
                        }
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(1000)
                Result(-1, output.toString(), true)
            } else {
                reader.join(1500)
                Result(process.exitValue(), output.toString(), false)
            }
        }.getOrElse {
            Result(-1, it.message.orEmpty(), false)
        }
    }

    private const val MAX_OUTPUT = 4 * 1024 * 1024
}
