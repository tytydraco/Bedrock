package com.draco.bedrock.utils

import android.annotation.SuppressLint
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.draco.bedrock.models.WorldFile
import com.draco.bedrock.repositories.constants.Minecraft
import com.draco.bedrock.repositories.constants.WorldFileTypes
import com.draco.bedrock.repositories.remote.GoogleDrive

class MinecraftWorldUtils(private val context: Context) {
    private val contentResolver = context.contentResolver

    /**
     * Get the level name of a Minecraft world folder
     *
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
     *
     * @param worldFolder The DocumentFile for the Minecraft World
     * @return True if this is a valid world folder
     */
    fun isValidWorld(worldFolder: DocumentFile) =
        worldFolder.name == Minecraft.WORLDS_FOLDER_NAME && worldFolder.isDirectory

    /**
     * Upload all Minecraft worlds
     *
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
     *
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
     *
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     */
    fun deleteAllDevice(rootDocumentFile: DocumentFile) {
        rootDocumentFile.listFiles().forEach {
            it.delete()
        }
    }

    /**
     * Delete all remote Minecraft worlds
     *
     * @param worldList List of WorldFiles
     */
    fun deleteAllCloud(worldList: List<WorldFile>) {
        worldList.forEach {
            deleteWorldFromDrive(it.id)
        }
    }

    /**
     * Delete a local Minecraft world
     *
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDevice(rootDocumentFile: DocumentFile, worldId: String) {
        rootDocumentFile.listFiles().find { it.name == worldId }?.delete()
    }

    /**
     * Delete a remote Minecraft world
     *
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDrive(worldId: String) {
        GoogleDrive.delete(worldId)
    }

    /**
     * Upload a Minecraft world to the cloud
     *
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     * @param worldId Folder ID to use to find what to delete
     */
    fun uploadWorldToDrive(rootDocumentFile: DocumentFile, worldId: String) {
        rootDocumentFile.listFiles().find { it.name == worldId }?.let {
            /* Delete world if it exists on the cloud already */
            GoogleDrive.find(worldId)?.let {
                GoogleDrive.delete(worldId)
            }

            DocumentFileZip(contentResolver).use { documentFileZip ->
                documentFileZip.addDirectoryContentsToZip(it)

                if (!GoogleDrive.exists(worldId))
                    GoogleDrive.create(worldId, getLevelName(it))
                GoogleDrive.Write(worldId).file(documentFileZip.tempFile)
            }
        }
    }

    /**
     * Download and extract a Minecraft world from the cloud
     *
     * @param rootDocumentFile Minecraft Worlds DocumentFile
     * @param worldId Folder ID to use to find what to delete
     */
    fun downloadWorldFromDrive(rootDocumentFile: DocumentFile, worldId: String) {
        if (GoogleDrive.exists(worldId)) {
            GoogleDrive.Read(worldId).inputStream().let {
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

    /**
     * Update the recycler adapter with all of our worlds
     *
     * @param rootDocumentFile Optional Minecraft Worlds DocumentFile
     * @return List of WorldFiles
     */
    @SuppressLint("NotifyDataSetChanged")
    fun list(rootDocumentFile: DocumentFile?): List<WorldFile> {
        /* Get both local and remote worlds */
        val localFiles = rootDocumentFile?.listFiles()
        val driveFiles = try {
            GoogleDrive.files()
        } catch (e: Exception) {
            null
        }

        val files = mutableListOf<WorldFile>()

        /* Parse local worlds */
        localFiles?.forEach {
            val name = getLevelName(it)?.trim()
            val id = it.name

            if (name != null && id != null) {
                files.add(
                    WorldFile(
                        name,
                        id,
                        WorldFileTypes.LOCAL
                    )
                )
            }
        }

        /* Parse remote worlds */
        driveFiles?.forEach {
            val matchingFile = files.find { localFile ->
                localFile.id == it.name && localFile.type == WorldFileTypes.LOCAL
            }

            if (matchingFile == null) {
                val name = it.description
                val id = it.name

                if (name != null && id != null) {
                    files.add(
                        WorldFile(
                            name,
                            id,
                            WorldFileTypes.REMOTE
                        )
                    )
                }
            } else {
                /* If we have this world logged already, it is present on local and remote */
                matchingFile.type = WorldFileTypes.LOCAL_REMOTE
            }
        }

        /* Sort worlds by their pretty name */
        return files.sortedBy { it.name }.toMutableList()
    }
}