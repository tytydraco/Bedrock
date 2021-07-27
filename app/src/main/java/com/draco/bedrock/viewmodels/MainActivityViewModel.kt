package com.draco.bedrock.viewmodels

import android.app.Application
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.draco.bedrock.models.DriveFile
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.utils.DocumentFileZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    val googleAccount = GoogleAccount(application.applicationContext)
    var googleDrive: GoogleDrive? = null

    /**
     * Initialize the Google Drive instance
     */
    fun initGoogleDrive() {
        val context = getApplication<Application>().applicationContext

        googleAccount.account?.let {
            googleDrive = GoogleDrive(context, it)
        }
    }

    /**
     * Upload the Google Drive world file
     */
    fun uploadWorldsDriveFile(root: DocumentFile) {
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch(Dispatchers.IO) {
            /* Zip all folders in the minecraftWorlds folder */
            for (file in root.listFiles()) {
                if (!file.isDirectory)
                    continue

                val driveFile = DriveFile(name = file.name)
                val zipBytes = DocumentFileZip(context, file).zip()
                googleDrive?.createFileIfNecessary(driveFile)
                googleDrive?.writeFileBytes(driveFile, zipBytes)
            }
        }
    }

    /**
     * Erases the subdirectory contents; creates one if it does not yet exist
     */
    private fun recreateSubDirectoryIfNecessary(root: DocumentFile, subDirectoryName: String): DocumentFile? {
        root.listFiles().find { it.name == subDirectoryName }?.delete()
        return root.createDirectory(subDirectoryName)
    }

    /**
     * Extracts the Google Drive world file
     */
    fun extractWorldsDriveFile(root: DocumentFile, worldFolderName: String) {
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch(Dispatchers.IO) {
            val driveFile = DriveFile(name = worldFolderName)
            if (googleDrive?.fileExists(driveFile) == true) {
                googleDrive?.readFileBytes(driveFile)?.let {
                    recreateSubDirectoryIfNecessary(root, worldFolderName)?.let { subFolder ->
                        DocumentFileZip(context, subFolder).unZip(it)
                    }
                }
            }
        }
    }
}