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
import com.draco.bedrock.utils.MinecraftWorldUtils
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    val googleAccount = GoogleAccount(application.applicationContext)
    var googleDrive: GoogleDrive? = null
    var worldsRecyclerAdapter: WorldsRecyclerAdapter? = null

    val minecraftWorldUtils = MinecraftWorldUtils(application.applicationContext)

    var rootDocumentFile: DocumentFile? = null

    private val _working = MutableLiveData<Int?>()
    val working: LiveData<Int?> = _working

    private val _worldList = MutableLiveData<List<WorldFile>>()
    val worldList: LiveData<List<WorldFile>> = _worldList

    init {
        /* Try to initialize the rootDocumentFile if we already granted it permissions */
        getPersistableUri()?.let {
            rootDocumentFile = DocumentFile.fromTreeUri(application.applicationContext, it)!!
        }
    }


    /**
     * Check if the user already has SAF permissions
     * @return The Uri for the Worlds folder, or null if it is not persisted
     */
    fun getPersistableUri(): Uri? {
        val context = getApplication<Application>().applicationContext

        return context
            .contentResolver
            .persistedUriPermissions
            .find { it.uri.toString().contains(MinecraftConstants.WORLDS_FOLDER_NAME) }
            ?.uri
    }

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

        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        rootDocumentFile = DocumentFile.fromTreeUri(context, uri)!!
        updateWorldsList()

        return true
    }

    /**
     * Update the recycler adapter with all of our worlds
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateWorldsList() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_updating_world_list)

            /* Get both local and remote worlds */
            val localFiles = rootDocumentFile?.listFiles()
            val driveFiles = try {
                googleDrive?.getFiles()
            } catch (e: Exception) {
                null
            }

            val files = mutableListOf<WorldFile>()

            /* Parse local worlds */
            localFiles?.forEach {
                val name = minecraftWorldUtils.getLevelName(it)?.trim()
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

            /* Parse remote worlds */
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
                } else {
                    /* If we have this world logged already, it is present on local and remote */
                    matchingLocalFile.type = WorldFileType.LOCAL_REMOTE
                }
            }

            /* Sort worlds by their pretty name */
            val newWorlds = files.sortedBy { it.name }.toMutableList()

            _worldList.postValue(newWorlds)

            /* Update the recycler adapter */
            withContext(Dispatchers.Main) {
                worldsRecyclerAdapter?.let {
                    it.worldFileList = newWorlds
                    it.notifyDataSetChanged()
                }
            }

            _working.postValue(null)
        }
    }

    /**
     * Catch exceptions, display an error for the user, and reset working progress
     * @param view A view to display the Snackbar on
     * @param runnable The problematic runnable to safely run
     */
    fun safeCatch(view: View, runnable: () -> Unit) {
        try {
            runnable()
        } catch (e: Exception) {
            e.printStackTrace()

            val context = getApplication<Application>().applicationContext

            /* Stop the progress bar if anything is loading */
            _working.postValue(null)

            /* Show the user a scary Snackbar */
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
     * Prepare the RecyclerView Adapter
     * @param context View-bound context
     */
    private fun prepareRecyclerAdapter(context: Context) {
        worldsRecyclerAdapter = WorldsRecyclerAdapter(context, mutableListOf()).apply {
            uploadHook = { view, worldName ->
                viewModelScope.launch(Dispatchers.IO) {
                    safeCatch(view) {
                        uploadWorldToDrive(worldName)
                        updateWorldsList()
                    }
                }
            }

            downloadHook = { view, worldName ->
                viewModelScope.launch(Dispatchers.IO) {
                    safeCatch(view) {
                        downloadWorldFromDrive(worldName)
                        updateWorldsList()
                    }
                }
            }

            deleteDeviceHook = { view, worldName ->
                viewModelScope.launch(Dispatchers.IO) {
                    safeCatch(view) {
                        deleteWorldFromDevice(worldName)
                        updateWorldsList()
                    }
                }
            }

            deleteCloudHook = { view, worldName ->
                viewModelScope.launch(Dispatchers.IO) {
                    safeCatch(view) {
                        deleteWorldFromDrive(worldName)
                        updateWorldsList()
                    }
                }
            }
        }
    }

    /**
     * Prepare the RecyclerView
     * @param context View-bound context
     * @param recycler RecyclerView to hook to
     */
    fun prepareRecycler(context: Context, recycler: RecyclerView) {
        if (worldsRecyclerAdapter == null)
            prepareRecyclerAdapter(context)

        recycler.apply {
            adapter = worldsRecyclerAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    /**
     * Upload all Minecraft worlds
     */
    fun uploadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_uploading)
            _worldList.value?.forEach {
                uploadWorldToDrive(it.id)
            }
            _working.postValue(null)

            updateWorldsList()
        }
    }

    /**
     * Download all Minecraft worlds
     * @param view View to use for catching exceptions
     */
    fun downloadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_downloading)
            _worldList.value?.forEach {
                downloadWorldFromDrive(it.id)
            }
            _working.postValue(null)

            updateWorldsList()
        }
    }

    /**
     * Delete all local Minecraft worlds
     * @param view View to use for catching exceptions
     */
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

    /**
     * Delete all remote Minecraft worlds
     * @param view View to use for catching exceptions
     */
    fun deleteAllCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_delete_cloud)
            _worldList.value?.forEach {
                deleteWorldFromDrive(it.id)
            }
            _working.postValue(null)

            updateWorldsList()
        }
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
     * Delete a local Minecraft world
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDevice(worldId: String) {
        _working.postValue(R.string.working_delete_device)
        rootDocumentFile?.listFiles()?.find { it.name == worldId }?.delete()
        _working.postValue(null)
    }

    /**
     * Delete a remote Minecraft world
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDrive(worldId: String) {
        _working.postValue(R.string.working_delete_cloud)
        val driveFile = DriveFile(name = worldId)
        googleDrive?.deleteFile(driveFile)
        _working.postValue(null)
    }

    /**
     * Upload a Minecraft world to the cloud
     * @param view View to use for catching exceptions
     * @param worldId Folder ID to use to find what to delete
     */
    fun uploadWorldToDrive(worldId: String) {
        val context = getApplication<Application>().applicationContext

        _working.postValue(R.string.working_uploading)

        rootDocumentFile?.listFiles()?.find { it.name == worldId }?.let {
            val driveFile = DriveFile(
                name = worldId,
                description = minecraftWorldUtils.getLevelName(it)
            )

            _working.postValue(R.string.working_zipping)
            val zipBytes = DocumentFileZip(context, it).zip()
            googleDrive?.createFileIfNecessary(driveFile)
            googleDrive?.writeFileBytes(driveFile, zipBytes)
        }

        _working.postValue(null)
    }

    /**
     * Download and extract a Minecraft world from the cloud
     * @param worldId Folder ID to use to find what to delete
     */
    fun downloadWorldFromDrive(worldId: String) {
        val context = getApplication<Application>().applicationContext

        _working.postValue(R.string.working_downloading)

        val driveFile = DriveFile(name = worldId)
        if (googleDrive?.fileExists(driveFile) == true) {
            googleDrive?.readFileBytes(driveFile)?.let {
                /* Recreate any existing world folders */
                rootDocumentFile?.listFiles()?.find { it.name == worldId }?.delete()
                rootDocumentFile?.createDirectory(worldId)?.let { subFolder ->
                    _working.postValue(R.string.working_unzipping)
                    DocumentFileZip(context, subFolder).unZip(it)
                }
            }
        }

        _working.postValue(null)
    }
}