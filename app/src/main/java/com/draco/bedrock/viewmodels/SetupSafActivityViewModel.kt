package com.draco.bedrock.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import com.draco.bedrock.utils.MinecraftWorldUtils

class SetupSafActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val minecraftWorldUtils = MinecraftWorldUtils(application.applicationContext)
    private val contentResolver = application.contentResolver

    /**
     * Store the persistable Uri and update the root document
     * @param uri OPEN_DOCUMENT_TREE uri
     * @return True if we persisted it, false if the selected world is invalid
     */
    fun takePersistableUri(uri: Uri): Boolean {
        val context = getApplication<Application>().applicationContext

        val selectedFolder = DocumentFile.fromTreeUri(context, uri)!!
        if (!minecraftWorldUtils.isValidWorld(selectedFolder))
            return false

        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        return true
    }
}