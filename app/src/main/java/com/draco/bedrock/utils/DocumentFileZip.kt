package com.draco.bedrock.utils

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentFileZip(
    private val contentResolver: ContentResolver
) : AutoCloseable {
    val tempFile: File = File.createTempFile("temp", "zip").also {
        it.deleteOnExit()
    }
    private val byteArrayOutputStream = tempFile.outputStream()
    private val zipOutputStream = ZipOutputStream(byteArrayOutputStream)

    /**
     * Recursively add files from directory to zip
     * @param rootFile Directory to recurse from
     * @param pwd The current parent working directory
     */
    fun addDirectoryContentsToZip(rootFile: DocumentFile, pwd: String = "") {
        for (file in rootFile.listFiles()) {
            val filePath = "$pwd${file.name}"
            if (file.isDirectory) {
                val filePathFolder = "$filePath/"
                addEmptyFolderToZip(filePathFolder)
                addDirectoryContentsToZip(file, filePathFolder)
            } else {
                addFileToZip(file, filePath)
            }
        }
    }

    /**
     * Read a DocumentFile and return a ByteArray of its contents
     * @param file DocumentFile to read from
     * @return The byte contents or null
     */
    fun readFile(file: DocumentFile): ByteArray? {
        contentResolver.openInputStream(file.uri).use {
            return it?.readBytes()
        }
    }

    /**
     * Create an empty folder in the zip file
     * @param path Path of the empty folder to add
     */
    fun addEmptyFolderToZip(path: String) {
        val zipEntry = ZipEntry(path)
        zipOutputStream.apply {
            putNextEntry(zipEntry)
            closeEntry()
        }
    }

    /**
     * Add a DocumentFile to the Zip file
     * @param file DocumentFile to add
     * @param path Where to place this file
     */
    fun addFileToZip(file: DocumentFile, path: String) {
        val zipEntry = ZipEntry(path)
        val content = readFile(file)
        zipOutputStream.apply {
            putNextEntry(zipEntry)
            write(content)
            closeEntry()
        }
    }

    /**
     * Close open streams and delete temp file
     */
    override fun close() {
        zipOutputStream.close()
        byteArrayOutputStream.close()
        tempFile.delete()
    }
}