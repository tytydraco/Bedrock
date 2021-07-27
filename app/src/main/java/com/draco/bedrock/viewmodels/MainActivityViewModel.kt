package com.draco.bedrock.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.draco.bedrock.R
import com.draco.bedrock.models.DriveFile
import com.draco.bedrock.models.WorldFile
import com.draco.bedrock.recyclers.WorldsRecyclerAdapter
import com.draco.bedrock.repositories.constants.MinecraftConstants
import com.draco.bedrock.repositories.constants.WorldFileType
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.utils.DocumentFileZip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    val googleAccount = GoogleAccount(application.applicationContext)
    var googleDrive: GoogleDrive? = null
    var worldsRecyclerAdapter: WorldsRecyclerAdapter? = null

    var rootDocumentFile: DocumentFile? = null

    private val _working = MutableLiveData<Int?>()
    val working: LiveData<Int?> = _working

    private val _worldList = MutableLiveData<List<WorldFile>>()
    val worldList: LiveData<List<WorldFile>> = _worldList

    init {
        getPersistableUri()?.let {
            rootDocumentFile = DocumentFile.fromTreeUri(application.applicationContext, it)!!
        }
    }

    /**
     * Check if the user has selected a valid worlds folder
     */
    fun isDocumentMinecraftWorldsFolder(file: DocumentFile) =
        file.name == MinecraftConstants.WORLDS_FOLDER_NAME && file.isDirectory

    /**
     * Check if the user already has SAF permissions
     */
    fun getPersistableUri(): Uri? {
        val context = getApplication<Application>().applicationContext

        return context
            .contentResolver
            .persistedUriPermissions
            .find {it.uri.toString().contains(MinecraftConstants.WORLDS_FOLDER_NAME) }
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
     * Update the recycler adapter with all of our worlds
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateWorldsList() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_updating_world_list)
            val localFiles = rootDocumentFile?.listFiles()
            val driveFiles = try {
                googleDrive?.getFiles()
            } catch (e: Exception) {
                null
            }

            val files = mutableListOf<WorldFile>()

            localFiles?.forEach {
                val name = getWorldNameForWorldFolder(it)?.trim()
                val id = it.name

                if (name != null && id != null) {
                    files.add(
                        WorldFile(
                            name,
                            id,
                            WorldFileType.LOCAL
                        )
                    )
                }
            }

            driveFiles?.forEach {
                val matchingLocalFile = files.find { localFile -> localFile.id == it.name }

                if (matchingLocalFile == null) {
                    val name = it.description
                    val id = it.name

                    if (name != null && id != null) {
                        files.add(
                            WorldFile(
                                name,
                                id,
                                WorldFileType.REMOTE
                            )
                        )
                    }
                } else
                    matchingLocalFile.type = WorldFileType.LOCAL_REMOTE
            }

            val newWorlds = files.sortedBy { it.name }.toMutableList()

            _worldList.postValue(newWorlds)

            withContext(Dispatchers.Main) {
                worldsRecyclerAdapter?.worldFileList = newWorlds
                worldsRecyclerAdapter?.notifyDataSetChanged()
            }

            _working.postValue(null)
        }
    }

    /**
     * Catch exceptions and reset working progress
     */
    private fun catchExceptions(view: View, runnable: () -> Unit) {
        try {
            runnable()
        } catch (e: Exception) {
            val context = getApplication<Application>().applicationContext

            e.printStackTrace()
            _working.postValue(null)

            val error = context.getString(R.string.snackbar_exception)
            viewModelScope.launch(Dispatchers.Main) {
                Snackbar.make(
                    view,
                    error,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Prepare the recycler view
     */
    fun prepareRecycler(context: Context, recycler: RecyclerView) {
        if (worldsRecyclerAdapter == null) {
            worldsRecyclerAdapter = WorldsRecyclerAdapter(context, mutableListOf()).apply {
                uploadHook = { view, worldName ->
                    viewModelScope.launch(Dispatchers.IO) {
                        catchExceptions(view) {
                            uploadWorldToDrive(worldName)
                            updateWorldsList()
                        }
                    }
                }

                downloadHook = { view, worldName ->
                    viewModelScope.launch(Dispatchers.IO) {
                        catchExceptions(view) {
                            downloadWorldFromDrive(worldName)
                            updateWorldsList()
                        }
                    }
                }

                deleteDeviceHook = { view, worldName ->
                    viewModelScope.launch(Dispatchers.IO) {
                        catchExceptions(view) {
                            deleteWorldFromDevice(worldName)
                            updateWorldsList()
                        }
                    }
                }

                deleteCloudHook = { view, worldName ->
                    viewModelScope.launch(Dispatchers.IO) {
                        catchExceptions(view) {
                            deleteWorldFromDrive(worldName)
                            updateWorldsList()
                        }
                    }
                }
            }
        }

        recycler.apply {
            adapter = worldsRecyclerAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    fun uploadAll(view: View) {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_uploading)
            catchExceptions(view) {
                _worldList.value?.forEach {
                    uploadWorldToDrive(it.id)
                }
            }
            _working.postValue(null)

            updateWorldsList()
        }
    }

    fun downloadAll(view: View) {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_downloading)
            catchExceptions(view) {
                _worldList.value?.forEach {
                    downloadWorldFromDrive(it.id)
                }
            }
            _working.postValue(null)

            updateWorldsList()
        }
    }

    fun deleteAllDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_delete_device)
            rootDocumentFile?.listFiles()?.forEach {
                it?.name?.let { name ->
                    deleteWorldFromDevice(name)
                }
            }
            _working.postValue(null)

            updateWorldsList()
        }
    }

    fun deleteAllCloud(view: View) {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_delete_cloud)
            catchExceptions(view) {
                _worldList.value?.forEach {
                    deleteWorldFromDrive(it.id)
                }
            }
            _working.postValue(null)

            updateWorldsList()
        }
    }

    /**
     * Erase a world file from device
     */
    fun deleteWorldFromDevice(worldId: String) {
        _working.postValue(R.string.working_delete_device)
        rootDocumentFile?.listFiles()?.find { it.name == worldId }?.delete()
        _working.postValue(null)
    }

    /**
     * Erase a world file from Google Drive
     */
    fun deleteWorldFromDrive(worldId: String) {
        _working.postValue(R.string.working_delete_cloud)
        val driveFile = DriveFile(name = worldId)
        googleDrive?.deleteFile(driveFile)
        _working.postValue(null)
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

        _working.postValue(R.string.working_uploading)

        rootDocumentFile?.listFiles()?.find { it.name == worldId }?.let {
            val driveFile = DriveFile(
                name = worldId,
                description = getWorldNameForWorldFolder(it)
            )

            _working.postValue(R.string.working_zipping)
            val zipBytes = DocumentFileZip(context, it).zip()
            googleDrive?.createFileIfNecessary(driveFile)
            googleDrive?.writeFileBytes(driveFile, zipBytes)
        }

        _working.postValue(null)
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

        _working.postValue(R.string.working_downloading)

        val driveFile = DriveFile(name = worldId)
        if (googleDrive?.fileExists(driveFile) == true) {
            googleDrive?.readFileBytes(driveFile)?.let {
                recreateSubDirectoryIfNecessary(worldId)?.let { subFolder ->
                    _working.postValue(R.string.working_unzipping)
                    DocumentFileZip(context, subFolder).unZip(it)
                }
            }
        }

        _working.postValue(null)
    }
}