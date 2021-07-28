package com.draco.bedrock.utils

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.draco.bedrock.repositories.constants.MinecraftConstants

class MinecraftWorldUtils(private val context: Context) {
    /**
     * Get the level name of a Minecraft world folder
     * @param worldFolder The DocumentFile for the Minecraft World
     * @return Level name string, or null
     */
    fun getLevelName(worldFolder: DocumentFile): String? {
        worldFolder.listFiles().find { it.name == MinecraftConstants.LEVEL_FILE_NAME }?.let {
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
        worldFolder.name == MinecraftConstants.WORLDS_FOLDER_NAME && worldFolder.isDirectory
}