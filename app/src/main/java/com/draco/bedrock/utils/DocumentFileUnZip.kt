package com.draco.bedrock.utils

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class DocumentFileUnZip(
    private val contentResolver: ContentResolver,
    private val documentFile: DocumentFile,
    inputStream: InputStream
) : AutoCloseable {
    private val zipInputStream = ZipInputStream(inputStream)

    /**
     * Iterate and extract all entries
     */
    fun extractZipEntryToDocumentFile() {
        val parentFileMap = mutableMapOf("" to documentFile)

        var zipEntry = zipInputStream.nextEntry
        while (zipEntry != null) {
            val zipEntryPwd = File(zipEntry.name).parent ?: ""
            val zipEntryBasename = File(zipEntry.name).name

            val parentFile = parentFileMap[zipEntryPwd]!!
            if (zipEntry.isDirectory) {
                parentFile.createDirectory(zipEntryBasename)?.let {
                    parentFileMap[zipEntry.name.removeSuffix("/")] = it
                }
            } else {
                parentFile.createFile("", zipEntryBasename)?.let {
                    writeFile(it)
                }
            }

            zipEntry = zipInputStream.nextEntry
        }
    }

    /**
     * Write the current ZipEntry contents to the document file
     *
     * @param file DocumentFile to write the next entry to
     */
    fun writeFile(file: DocumentFile) {
        contentResolver.openOutputStream(file.uri).use {
            if (it != null)
                zipInputStream.copyTo(it)
        }
    }

    /**
     * Close open streams
     */
    override fun close() {
        zipInputStream.close()
    }
}