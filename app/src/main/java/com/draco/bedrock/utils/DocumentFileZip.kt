package com.draco.bedrock.utils

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DocumentFileZip(
    context: Context,
    private val documentFile: DocumentFile
) {
    private val contentResolver = context.contentResolver

    /**
     * Zip the contents of the class documentFile and return the byte array output
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
         */
        fun readFile(file: DocumentFile): ByteArray? {
            contentResolver.openInputStream(file.uri).use {
                return it?.readBytes()
            }
        }

        /**
         * Create an empty folder in the zip file
         */
        fun addEmptyFolderToZip(path: String) {
            Log.d("DocumentZipFile", "Adding Folder: $path")

            val zipEntry = ZipEntry(path)
            zipOutputStream.apply {
                putNextEntry(zipEntry)
                closeEntry()
            }
        }

        /**
         * Add a DocumentFile to the Zip file
         */
        fun addFileToZip(file: DocumentFile, path: String) {
            Log.d("DocumentZipFile", "Adding: $path")

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
                Log.d("DocumentZipFile", "Extracting: ${zipEntry.name}")

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