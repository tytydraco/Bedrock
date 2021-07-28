package com.draco.bedrock.utils

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DocumentFileZip(
    private val contentResolver: ContentResolver,
    private val documentFile: DocumentFile
) {
    /**
     * Zip the contents of the class documentFile and return the byte array output
     * @return ByteArray of the Zip file contents
     */
    fun zip(): ByteArray {
        val zip = Zip().apply {
            addDirectoryContentsToZip(documentFile)
            close()
        }

        return zip.byteArrayOutputStream.toByteArray()
    }

    /**
     * UnZip the contents of the zip byte array into the class document file root directory
     * @param zipBytes ByteArray of the Zip file contents
     */
    fun unZip(zipBytes: ByteArray) {
       UnZip(zipBytes).apply {
            extractZipEntryToDocumentFile()
            close()
        }
    }

    private inner class Zip {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val zipOutputStream = ZipOutputStream(byteArrayOutputStream)

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
         * Close open streams
         */
        fun close() {
            zipOutputStream.closeEntry()
            byteArrayOutputStream.close()
        }
    }

    private inner class UnZip(zipBytes: ByteArray) {
        val byteArrayInputStream = ByteArrayInputStream(zipBytes)
        val zipInputStream = ZipInputStream(byteArrayInputStream)

        /**
         * Iterate and extract all entries
         */
        fun extractZipEntryToDocumentFile() {
            val parentFileMap = mutableMapOf(
                "" to documentFile
            )

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
        fun close() {
            zipInputStream.close()
            byteArrayInputStream.close()
        }
    }
}