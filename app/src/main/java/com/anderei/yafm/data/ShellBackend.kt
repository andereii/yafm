package com.anderei.yafm.data

import rikka.shizuku.Shizuku
import com.anderei.yafm.util.shellQuoted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShellResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
) {
    val stdoutText: String get() = String(stdout, Charsets.UTF_8)
}

interface ShellExecutor {
    val label: String
    suspend fun run(script: String, timeoutMillis: Long = 30_000L): ShellResult
}

/** Executes scripts as the ADB shell user through Shizuku. */
class ShizukuShellExecutor : ShellExecutor {

    override val label: String = "Shizuku"

    override suspend fun run(script: String, timeoutMillis: Long): ShellResult =
        withContext(Dispatchers.IO) {
            if (!Shizuku.pingBinder()) {
                throw IOException("Shizuku is not running")
            }
            val method = newProcessMethod
                ?: throw IOException("This Shizuku version doesn't expose shell access")

            val process = try {
                method.invoke(null, arrayOf("sh", "-c", script), null, null) as Process
            } catch (e: InvocationTargetException) {
                throw IOException(e.cause?.message ?: "Shizuku rejected the request", e.cause)
            } catch (e: Exception) {
                throw IOException(e.message ?: "Shizuku error", e)
            }

            process.awaitResult(timeoutMillis)
        }

    companion object {
        /**
         * Shizuku 13.1.5 exposes [Shizuku.newProcess] as a private, deprecated convenience
         * wrapper around its remote shell. It still returns a regular [Process], so the
         * reflection keeps working and is the smallest way to run shell commands.
         */
        private val newProcessMethod: Method? = runCatching {
            val stringArray = arrayOf("").javaClass
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    stringArray,
                    stringArray,
                    String::class.java,
                )
                .apply { isAccessible = true }
        }.getOrNull()
    }
}

/** Executes scripts as root through Magisk / KernelSU / APatch su. */
class RootShellExecutor : ShellExecutor {

    override val label: String = "Root"

    private val candidates = listOf(
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/debug_ramdisk/su",
        "/sbin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
    )

    /**
     * `su` normally inherits the requesting app's mount namespace, where /data/user/0 is
     * restricted to the app's own data. Magisk and KernelSU support --mount-master to run in
     * the global namespace; [PrivilegeManager] probes it and calls [selectMountMaster].
     */
    @Volatile
    private var mountMaster: Boolean = true

    fun selectMountMaster(enabled: Boolean) {
        mountMaster = enabled
    }

    override suspend fun run(script: String, timeoutMillis: Long): ShellResult =
        runInternal(script, timeoutMillis, mountMaster)

    suspend fun runPlain(script: String, timeoutMillis: Long): ShellResult =
        runInternal(script, timeoutMillis, mountMaster = false)

    suspend fun runMountMaster(script: String, timeoutMillis: Long): ShellResult =
        runInternal(script, timeoutMillis, mountMaster = true)

    private suspend fun runInternal(
        script: String,
        timeoutMillis: Long,
        mountMaster: Boolean,
    ): ShellResult = withContext(Dispatchers.IO) {
        var lastError: IOException? = null
        for (candidate in candidates) {
            val command = if (mountMaster) {
                listOf(candidate, "--mount-master", "-c", script)
            } else {
                listOf(candidate, "-c", script)
            }
            val process = try {
                ProcessBuilder(command).start()
            } catch (e: IOException) {
                lastError = e
                continue
            }
            return@withContext process.awaitResult(timeoutMillis)
        }
        throw IOException(
            "Root unavailable: no usable su binary was found",
            lastError,
        )
    }

    /** Checks for an su binary without triggering a root prompt. */
    suspend fun suBinaryPresent(): Boolean = withContext(Dispatchers.IO) {
        val script = buildString {
            appendLine("command -v su >/dev/null 2>&1 && exit 0")
            appendLine(
                "for p in /system/bin/su /system/xbin/su /debug_ramdisk/su /sbin/su " +
                    "/data/adb/ksu/bin/su /data/adb/ap/bin/su; do",
            )
            appendLine("  [ -x \"\$p\" ] && exit 0")
            appendLine("done")
            appendLine("exit 1")
        }
        try {
            ProcessBuilder("sh", "-c", script).start().awaitResult(10_000L).exitCode == 0
        } catch (e: IOException) {
            false
        }
    }
}

/**
 * File operations executed through [shell]. Used for Shizuku and root modes.
 *
 * Listings use one batched `stat` call for speed and fall back to a NUL-delimited
 * (filename-safe) loop when a name contains a newline.
 */
class ShellFileBackend(
    private val shell: ShellExecutor,
    override val navigationRoot: File,
) : FileBackend {

    override suspend fun list(directory: File): List<FileItem> {
        val result = shell.run(listScript(directory))
        if (result.exitCode != 0) {
            throw IOException(friendlyError(result, "Unable to read this folder"))
        }
        val items = parseListing(directory, result.stdout)
        // A directory with no read permission can still be entered (execute bit), so the
        // listing succeeds with empty output. Surface the permission error instead of
        // pretending the folder is empty.
        if (items.isEmpty() && "Permission denied" in result.stderr) {
            throw IOException(
                "Permission denied. Enable Shizuku or root access to open this folder.",
            )
        }
        return items
    }

    override suspend fun createFolder(parent: File, name: String): File {
        val target = File(parent, name)
        val script = """
            if [ -e ${target.absolutePath.shellQuoted()} ]; then
              echo "An item named \"$name\" already exists" >&2
              exit 3
            fi
            mkdir -- ${target.absolutePath.shellQuoted()}
        """.trimIndent()
        shell.run(script).requireSuccess("Couldn't create folder")
        return target
    }

    override suspend fun rename(source: File, newName: String): File {
        val parent = source.parentFile ?: throw IOException("This item has no parent folder")
        val target = File(parent, newName)
        val script = """
            if [ -e ${target.absolutePath.shellQuoted()} ]; then
              echo "An item named \"$newName\" already exists" >&2
              exit 3
            fi
            mv -- ${source.absolutePath.shellQuoted()} ${target.absolutePath.shellQuoted()}
        """.trimIndent()
        shell.run(script).requireSuccess("Couldn't rename this item")
        return target
    }

    override suspend fun delete(files: List<File>): OperationResult {
        if (files.isEmpty()) return OperationResult(0, 0)
        val script = buildString {
            appendLine("ok=0; fail=0")
            files.forEach { file ->
                appendLine(
                    "if rm -rf -- ${file.absolutePath.shellQuoted()}; then " +
                        "ok=\$((ok+1)); else fail=\$((fail+1)); fi",
                )
            }
            appendLine("printf '%s %s\\n' \"\$ok\" \"\$fail\"")
        }
        val result = shell.run(script)
        if (result.exitCode != 0) {
            throw IOException(friendlyError(result, "Couldn't delete these items"))
        }
        return result.parseCounts()
    }

    override suspend fun copy(files: List<File>, targetDirectory: File): OperationResult {
        val plan = planTransfers(files, targetDirectory) ?: return OperationResult(0, files.size)
        val script = buildString {
            appendLine("ok=0; fail=0")
            plan.pairs.forEach { (source, destination) ->
                appendLine(
                    "if cp -R -- ${source.absolutePath.shellQuoted()} " +
                        "${destination.absolutePath.shellQuoted()}; then " +
                        "ok=\$((ok+1)); else fail=\$((fail+1)); fi",
                )
            }
            appendLine("printf '%s %s\\n' \"\$ok\" \"\$fail\"")
        }
        val result = shell.run(script, timeoutMillis = 600_000L)
        if (result.exitCode != 0) {
            throw IOException(friendlyError(result, "Couldn't copy these items"))
        }
        val counts = result.parseCounts()
        return OperationResult(counts.succeeded, counts.failed + plan.skipped)
    }

    override suspend fun move(files: List<File>, targetDirectory: File): OperationResult {
        val plan = planTransfers(files, targetDirectory) ?: return OperationResult(0, files.size)
        val script = buildString {
            appendLine("ok=0; fail=0")
            plan.pairs.forEach { (source, destination) ->
                val from = source.absolutePath.shellQuoted()
                val to = destination.absolutePath.shellQuoted()
                appendLine(
                    "if mv -- $from $to 2>/dev/null; then ok=\$((ok+1)); " +
                        "elif cp -R -- $from $to && rm -rf -- $from; then ok=\$((ok+1)); " +
                        "else fail=\$((fail+1)); fi",
                )
            }
            appendLine("printf '%s %s\\n' \"\$ok\" \"\$fail\"")
        }
        val result = shell.run(script, timeoutMillis = 600_000L)
        if (result.exitCode != 0) {
            throw IOException(friendlyError(result, "Couldn't move these items"))
        }
        val counts = result.parseCounts()
        return OperationResult(counts.succeeded, counts.failed + plan.skipped)
    }

    override suspend fun stage(source: File, stagingDirectory: File): File {
        val existing = stagingDirectory.list()?.toMutableSet() ?: mutableSetOf()
        val name = uniqueName(source.name, source.isDirectory) { it in existing }
        val target = File(stagingDirectory, name)
        // The copied file can be owned by root or shell. Make it readable/traversable so
        // FileProvider (running as the app UID) can serve it to the receiving app.
        val script = "mkdir -p -- ${stagingDirectory.absolutePath.shellQuoted()} && " +
            "cp -R -- ${source.absolutePath.shellQuoted()} ${target.absolutePath.shellQuoted()} && " +
            "chmod -R a+rX -- ${target.absolutePath.shellQuoted()}"
        val result = shell.run(script, timeoutMillis = 600_000L)
        if (result.exitCode != 0) {
            throw IOException(
                friendlyError(result, "Couldn't copy this file to a readable location"),
            )
        }
        if (!target.exists()) {
            throw IOException("Couldn't copy this file to a readable location")
        }
        return target
    }

    // ------------------------------------------------------------------ internals

    private class TransferPlan(
        val pairs: List<Pair<File, File>>,
        val skipped: Int,
    )

    private suspend fun planTransfers(
        files: List<File>,
        targetDirectory: File,
    ): TransferPlan? {
        if (files.isEmpty()) return null
        val existing = list(targetDirectory).mapTo(mutableSetOf()) { it.name }
        var skipped = 0
        val pairs = files.mapNotNull { source ->
            // Never copy/move a folder inside itself.
            if (isInside(source, targetDirectory)) {
                skipped++
                return@mapNotNull null
            }
            val name = uniqueName(source.name, source.isDirectory) { it in existing }
            existing += name
            source to File(targetDirectory, name)
        }
        return TransferPlan(pairs, skipped)
    }

    private fun listScript(directory: File): String {
        val path = directory.absolutePath.shellQuoted()
        return """
            cd -- $path || exit 2
            expected=${'$'}(find . -mindepth 1 -maxdepth 1 -print0 | tr -cd "\000" | wc -c)
            out=${'$'}(find . -mindepth 1 -maxdepth 1 -exec stat -L -c '%f|%s|%Y|%u|%n' {} +)
            actual=${'$'}(printf '%s\n' "${'$'}out" | wc -l)
            bad=${'$'}(printf '%s\n' "${'$'}out" | awk -F'|' 'NF<5{c++} END{print c+0}')
            if [ "${'$'}expected" -eq "${'$'}actual" ] && [ "${'$'}bad" -eq 0 ]; then
              printf 'L\n'
              printf '%s\n' "${'$'}out"
            else
              printf 'N\n'
              find . -mindepth 1 -maxdepth 1 -print0 | while IFS= read -r -d '' f; do
                set -- ${'$'}(stat -L -c '%f %s %Y %u' -- "${'$'}f" 2>/dev/null)
                [ ${'$'}# -eq 4 ] || set -- 0 0 0 0
                printf '%s\0%s\0%s\0%s\0%s\0' "${'$'}1" "${'$'}2" "${'$'}3" "${'$'}4" "${'$'}{f##*/}"
              done
            fi
        """.trimIndent()
    }

    private fun parseListing(directory: File, bytes: ByteArray): List<FileItem> {
        if (bytes.isEmpty()) return emptyList()
        val newline = bytes.indexOf('\n'.code.toByte())
        if (newline < 0) return emptyList()

        val header = String(bytes, 0, newline, Charsets.UTF_8)
        val body = bytes.copyOfRange(newline + 1, bytes.size)
        return if (header == "N") parseNulRecords(directory, body) else parseLines(directory, body)
    }

    private fun parseLines(directory: File, body: ByteArray): List<FileItem> {
        if (body.isEmpty()) return emptyList()
        val items = mutableListOf<FileItem>()
        String(body, Charsets.UTF_8).lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('|', limit = 5)
            if (parts.size < 5) return@forEach

            val mode = parts[0].toLongOrNull(16) ?: 0L
            val size = parts[1].toLongOrNull() ?: 0L
            val modifiedSeconds = parts[2].toLongOrNull() ?: 0L
            val ownerUid = parts[3].toIntOrNull()
            val name = parts[4].removePrefix("./")
            if (name.isEmpty()) return@forEach

            val isDirectory = (mode and S_IFMT) == S_IFDIR
            items += FileItem.from(
                file = File(directory, name),
                isDirectory = isDirectory,
                size = size,
                lastModified = modifiedSeconds * 1000L,
                ownerUid = ownerUid,
            )
        }
        return items
    }

    private fun parseNulRecords(directory: File, body: ByteArray): List<FileItem> {
        val fields = ArrayList<String>()
        var start = 0
        for (index in body.indices) {
            if (body[index] == 0.toByte()) {
                fields += String(body, start, index - start, Charsets.UTF_8)
                start = index + 1
            }
        }

        val items = mutableListOf<FileItem>()
        var index = 0
        while (index + 4 < fields.size) {
            val mode = fields[index].toLongOrNull(16) ?: 0L
            val size = fields[index + 1].toLongOrNull() ?: 0L
            val modifiedSeconds = fields[index + 2].toLongOrNull() ?: 0L
            val ownerUid = fields[index + 3].toIntOrNull()
            val name = fields[index + 4]
            index += 5
            if (name.isEmpty()) continue

            val isDirectory = (mode and S_IFMT) == S_IFDIR
            items += FileItem.from(
                file = File(directory, name),
                isDirectory = isDirectory,
                size = size,
                lastModified = modifiedSeconds * 1000L,
                ownerUid = ownerUid,
            )
        }
        return items
    }

    private fun ShellResult.parseCounts(): OperationResult {
        val parts = stdoutText.trim().split(' ')
        val ok = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val failed = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return OperationResult(ok, failed)
    }

    private fun friendlyError(result: ShellResult, fallback: String): String {
        val firstLine = result.stderr.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: return fallback
        val cleaned = firstLine
            .removePrefix("find: .: ")
            .removePrefix("stat: ")
            .removePrefix("cp: ")
            .removePrefix("mv: ")
            .removePrefix("rm: ")
            .removePrefix("mkdir: ")
            .trim()
        return when {
            cleaned.isEmpty() -> fallback
            "Permission denied" in cleaned ->
                "Permission denied. Enable Shizuku or root access to open this folder."
            else -> cleaned
        }
    }

    private companion object {
        const val S_IFMT = 0xF000L
        const val S_IFDIR = 0x4000L
    }
}

private fun ShellResult.requireSuccess(fallback: String) {
    if (exitCode != 0) {
        val line = stderr.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        throw IOException(
            when {
                line.isNullOrEmpty() -> fallback
                "Permission denied" in line -> "Permission denied"
                else -> line
            },
        )
    }
}

/** Reads stdout/stderr concurrently and enforces a timeout. */
private fun Process.awaitResult(timeoutMillis: Long): ShellResult {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()

    val outThread = Thread {
        runCatching { inputStream.copyTo(out) }
    }.apply { isDaemon = true; start() }
    val errThread = Thread {
        runCatching { errorStream.copyTo(err) }
    }.apply { isDaemon = true; start() }

    // Don't use Process.waitFor(timeout, unit): Shizuku's remote process wraps
    // exitValue() in a RuntimeException instead of IllegalThreadStateException, which makes
    // the default timed wait throw "process hasn't exited". A watcher thread is portable.
    val exited = CountDownLatch(1)
    Thread {
        runCatching { waitFor() }
        exited.countDown()
    }.apply {
        isDaemon = true
        start()
    }

    val finished = try {
        exited.await(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
    if (!finished) destroy()

    runCatching { outThread.join(5_000L) }
    runCatching { errThread.join(5_000L) }

    return ShellResult(
        exitCode = if (finished) runCatching { exitValue() }.getOrDefault(-1) else -1,
        stdout = out.toByteArray(),
        stderr = if (finished) {
            err.toString(Charsets.UTF_8.name())
        } else {
            "Command timed out"
        },
    )
}
