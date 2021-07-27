package com.draco.bedrock.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.draco.bedrock.models.DriveFile
import com.draco.bedrock.models.WorldFile
import com.draco.bedrock.recyclers.WorldsRecyclerAdapter
import com.draco.bedrock.repositories.constants.WorldFileType
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.utils.DocumentFileZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    val googleAccount = GoogleAccount(application.applicationContext)
    var googleDrive: GoogleDrive? = null
    var worldsRecyclerAdapter: WorldsRecyclerAdapter? = null

    var rootDocumentFile: DocumentFile? = null

    private val _working = MutableLiveData<Boolean>()
    val working: LiveData<Boolean> = _working

    init {
        getPersistableUri()?.let {
            rootDocumentFile = DocumentFile.fromTreeUri(application.applicationContext, it)!!
        }
    }

    /**
     * Check if the user has selected a valid worlds folder
     */
    fun isDocumentMinecraftWorldsFolder(file: DocumentFile) = file.name == "minecraftWorlds" && file.isDirectory

    /**
     * Check if the user already has SAF permissions
     */
    fun getPersistableUri(): Uri? {
        val context = getApplication<Application>().applicationContext

        return context
            .contentResolver
            .persistedUriPermissions
            .find {it.uri.toString().contains("minecraftWorlds") }
            ?.uri
    }

    /**
     * Store the persistable Uri and update the root document; return false if bad directory
     */
    fun takePersistableUri(uri: Uri): Boolean {
        val context = getApplication<Application>().applicationContext

        val selectedFolder = DocumentFile.fromTreeUri(context, uri)!!
        if (!isDocumentMinecraftWorldsFolder(selectedFolder))
            return false

        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        rootDocumentFile = DocumentFile.fromTreeUri(context, uri)!!
        updateWorldsList()

        return true
    }

    fun getWorldNameForWorldFolder(worldFolder: DocumentFile): String? {
        val context = getApplication<Application>().applicationContext

        worldFolder.listFiles().find { it.name == "levelname.txt" }?.let {
            context.contentResolver.openInputStream(it.uri).use { inputStream ->
                inputStream?.bufferedReader().use { bufferedReader ->
                    return bufferedReader?.readText()
                }
            }
        }

        return null
    }

    /**
     * Update the recycler adapter with all of our worlds
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateWorldsList() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(true)
            val localFiles = rootDocumentFile?.listFiles()
            val driveFiles = googleDrive?.getFiles()

            val files = mutableListOf<WorldFile>()

            localFiles?.forEach {
                files.add(WorldFile(
                    getWorldNameForWorldFolder(it)!!,
                    it.name!!,
                    WorldFileType.LOCAL
                ))
            }

            driveFiles?.forEach {
                val matchingLocalFile = files.find { localFile -> localFile.id == it.name }

                if (matchingLocalFile == null)
                    files.add(WorldFile(
                        it.description ?: "?",
                        it.name ?: "?",
                        WorldFileType.REMOTE
                    ))
                else
                    matchingLocalFile.type = WorldFileType.LOCAL_REMOTE
            }

            withContext(Dispatchers.Main) {
                worldsRecyclerAdapter?.worldFileList = files.sortedBy { it.name }.toMutableList()
                worldsRecyclerAdapter?.notifyDataSetChanged()
            }

            _working.postValue(false)
        }
    }

    /**
     * Prepare the recycler view
     */
    fun prepareRecycler(context: Context, recycler: RecyclerView) {
        worldsRecyclerAdapter = WorldsRecyclerAdapter(context, mutableListOf()).apply {
            uploadHook = {
                viewModelScope.launch(Dispatchers.IO) {
                    uploadWorldToDrive(it)
                    updateWorldsList()
                }
            }

            downloadHook = {
                viewModelScope.launch(Dispatchers.IO) {
                    downloadWorldFromDrive(it)
                    updateWorldsList()
                }
            }

            deleteCloudHook = {
                viewModelScope.launch(Dispatchers.IO) {
                    deleteWorldFromDrive(it)
                    updateWorldsList()
                }
            }
        }

        recycler.apply {
            adapter = worldsRecyclerAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    /**
     * Erase a world file from Google Drive
     */
    fun deleteWorldFromDrive(worldId: String) {
        _working.postValue(true)

        val driveFile = DriveFile(name = worldId)
        googleDrive?.deleteFile(driveFile)

        _working.postValue(false)
    }

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
    fun uploadWorldToDrive(worldId: String) {
        val context = getApplication<Application>().applicationContext

        _working.postValue(true)

        rootDocumentFile?.listFiles()?.find { it.name == worldId }?.let {
            val driveFile = DriveFile(
                name = worldId,
                description = getWorldNameForWorldFolder(it)
            )
            val zipBytes = DocumentFileZip(context, it).zip()
            googleDrive?.createFileIfNecessary(driveFile)
            googleDrive?.writeFileBytes(driveFile, zipBytes)
        }

        _working.postValue(false)
    }

    /**
     * Erases the subdirectory contents; creates one if it does not yet exist
     */
    private fun recreateSubDirectoryIfNecessary(subDirectoryName: String): DocumentFile? {
        rootDocumentFile?.listFiles()?.find { it.name == subDirectoryName }?.delete()
        return rootDocumentFile?.createDirectory(subDirectoryName)
    }

    /**
     * Extracts the Google Drive world file
     */
    fun downloadWorldFromDrive(worldId: String) {
        val context = getApplication<Application>().applicationContext

        _working.postValue(true)

        val driveFile = DriveFile(name = worldId)
        if (googleDrive?.fileExists(driveFile) == true) {
            googleDrive?.readFileBytes(driveFile)?.let {
                recreateSubDirectoryIfNecessary(worldId)?.let { subFolder ->
                    DocumentFileZip(context, subFolder).unZip(it)
                }
            }
        }

        _working.postValue(false)
    }
}