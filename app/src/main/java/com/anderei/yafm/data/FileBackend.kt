package com.anderei.yafm.data

import java.io.File

enum class AccessMode {
    /** Standard scoped-storage access. */
    STANDARD,

    /** ADB shell level access through Shizuku (can also be root if Shizuku runs as root). */
    SHIZUKU,

    /** Root access through a Magisk / KernelSU / APatch su binary. */
    ROOT,
}

/**
 * Abstraction over the different ways YAFM can reach the filesystem.
 *
 * The standard implementation uses [java.io.File]; the privileged implementations run shell
 * commands through Shizuku or su.
 */
interface FileBackend {

    /** Highest directory the user is allowed to navigate to with this backend. */
    val navigationRoot: File

    suspend fun list(directory: File): List<FileItem>

    suspend fun createFolder(parent: File, name: String): File

    suspend fun rename(source: File, newName: String): File

    suspend fun delete(files: List<File>): OperationResult

    suspend fun copy(files: List<File>, targetDirectory: File): OperationResult

    suspend fun move(files: List<File>, targetDirectory: File): OperationResult

    /**
     * Returns a path the app (and other apps via FileProvider) can open directly.
     * Privileged backends copy the file into [stagingDirectory] when the app itself can't read it.
     */
    suspend fun stage(source: File, stagingDirectory: File): File

    /** Optional: finds ad-like files under app data using a privileged backend. */
    suspend fun findAdFiles(androidDataRoot: File): List<FileItem> = emptyList()
}
