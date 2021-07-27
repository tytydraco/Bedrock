package com.draco.bedrock.viewmodels

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.draco.bedrock.repositories.constants.GoogleDriveFiles
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

        viewModelScope.launch {
            val zipBytes = DocumentFileZip(context, root).zip()
            googleDrive?.createFileIfNecessary(GoogleDriveFiles.cloudWorldZip)
            googleDrive?.writeFileBytes(GoogleDriveFiles.cloudWorldZip, zipBytes)
        }
    }

    /**
     * Extracts the Google Drive world file
     */
    fun extractWorldsDriveFile(root: DocumentFile) {
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch(Dispatchers.IO) {
            if (googleDrive?.fileExists(GoogleDriveFiles.cloudWorldZip) == true) {
                val zipBytes = googleDrive?.readFileBytes(GoogleDriveFiles.cloudWorldZip)?.let {
                    DocumentFileZip(context, root).unZip(it)
                }
            }
        }
    }
}