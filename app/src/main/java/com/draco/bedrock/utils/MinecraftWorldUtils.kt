package com.draco.bedrock.utils

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.draco.bedrock.models.DriveFile
import com.draco.bedrock.models.WorldFile
import com.draco.bedrock.repositories.constants.Minecraft
import com.draco.bedrock.repositories.remote.GoogleDrive

class MinecraftWorldUtils(private val context: Context) {
    private val contentResolver = context.contentResolver

    /**
     * Get the level name of a Minecraft world folder
     * @param worldFolder The DocumentFile for the Minecraft World
     * @return Level name string, or null
     */
    fun getLevelName(worldFolder: DocumentFile): String? {
        worldFolder.listFiles().find { it.name == Minecraft.LEVEL_FILE_NAME }?.let {
            context.contentResolver.openInputStream(it.uri).use { inputStream ->
                inputStream?.bufferedReader().use { bufferedReader ->
                    return bufferedReader?.readText()
                }
            }
        }

        return null
    }

    /**
     * Check if the user has selected a valid worlds folder
     * @param worldFolder The DocumentFile for the Minecraft World
     * @return True if this is a valid world folder
     */
    fun isValidWorld(worldFolder: DocumentFile) =
        worldFolder.name == Minecraft.WORLDS_FOLDER_NAME && worldFolder.isDirectory

    /**
     * Upload all Minecraft worlds
     * @param worldList List of WorldFiles
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     */
    fun uploadAll(worldList: List<WorldFile>, rootDocumentFile: DocumentFile) {
        worldList.forEach {
            uploadWorldToDrive(rootDocumentFile, it.id)
        }
    }

    /**
     * Download all Minecraft worlds
     * @param worldList List of WorldFiles
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     */
    fun downloadAll(worldList: List<WorldFile>, rootDocumentFile: DocumentFile) {
        worldList.forEach {
            downloadWorldFromDrive(rootDocumentFile, it.id)
        }
    }

    /**
     * Delete all local Minecraft worlds
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     */
    fun deleteAllDevice(rootDocumentFile: DocumentFile) {
        rootDocumentFile.listFiles().forEach {
            it.delete()
        }
    }

    /**
     * Delete all remote Minecraft worlds
     * @param worldList List of WorldFiles
     */
    fun deleteAllCloud(worldList: List<WorldFile>) {
        worldList.forEach {
            deleteWorldFromDrive(it.id)
        }
    }

    /**
     * Delete a local Minecraft world
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDevice(rootDocumentFile: DocumentFile, worldId: String) {
        rootDocumentFile.listFiles().find { it.name == worldId }?.delete()
    }

    /**
     * Delete a remote Minecraft world
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDrive(worldId: String) {
        val driveFile = DriveFile(name = worldId)
        GoogleDrive.deleteFile(driveFile)
    }

    /**
     * Upload a Minecraft world to the cloud
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     * @param worldId Folder ID to use to find what to delete
     */
    fun uploadWorldToDrive(rootDocumentFile: DocumentFile, worldId: String) {
        rootDocumentFile.listFiles().find { it.name == worldId }?.let { documentFile ->
            val driveFile = DriveFile(
                name = worldId,
                description = getLevelName(documentFile)
            )

            DocumentFileZip(contentResolver).use {
                it.addDirectoryContentsToZip(documentFile)

                GoogleDrive.createFileIfNecessary(driveFile)
                GoogleDrive.writeFileRaw(driveFile, it.tempFile)
            }
        }
    }

    /**
     * Download and extract a Minecraft world from the cloud
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     * @param worldId Folder ID to use to find what to delete
     */
    fun downloadWorldFromDrive(rootDocumentFile: DocumentFile, worldId: String) {
        val driveFile = DriveFile(name = worldId)
        if (GoogleDrive.fileExists(driveFile)) {
            GoogleDrive.readFileInputStream(driveFile).let {
                /* Recreate any existing world folders */
                deleteWorldFromDevice(rootDocumentFile, worldId)
                rootDocumentFile.createDirectory(worldId)?.let { subFolder ->
                    DocumentFileUnZip(contentResolver, subFolder, it).use {
                        it.extractZipEntryToDocumentFile()
                    }
                }
            }
        }
    }
}