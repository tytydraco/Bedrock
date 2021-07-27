package com.draco.bedrock.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
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
    lateinit var rootDocumentFile: DocumentFile

    /**
     * Update the recycler adapter with all of our worlds
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateWorldsList() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = mutableListOf<WorldFile>()

            rootDocumentFile.listFiles().forEach {
                it.name?.let { name ->
                    files.add(WorldFile(name, WorldFileType.LOCAL))
                }
            }

            googleDrive?.getFiles()?.forEach {
                val matchingLocalFile = files.find { localFile -> localFile.name == it.name }

                if (matchingLocalFile == null)
                    files.add(WorldFile(it.name, WorldFileType.REMOTE))
                else
                    matchingLocalFile.type = WorldFileType.LOCAL_REMOTE
            }

            withContext(Dispatchers.Main) {
                worldsRecyclerAdapter?.worldFileList = files.sortedBy { it.name }.toMutableList()
                worldsRecyclerAdapter?.notifyDataSetChanged()
            }
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
    fun deleteWorldFromDrive(worldName: String) {
        val driveFile = DriveFile(name = worldName)
        googleDrive?.deleteFile(driveFile)
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
    fun uploadWorldToDrive(worldName: String) {
        val context = getApplication<Application>().applicationContext

        rootDocumentFile.listFiles().find { it.name == worldName }?.let {
            val driveFile = DriveFile(name = worldName)
            val zipBytes = DocumentFileZip(context, it).zip()
            googleDrive?.createFileIfNecessary(driveFile)
            googleDrive?.writeFileBytes(driveFile, zipBytes)
        }
    }

    /**
     * Erases the subdirectory contents; creates one if it does not yet exist
     */
    private fun recreateSubDirectoryIfNecessary(subDirectoryName: String): DocumentFile? {
        rootDocumentFile.listFiles().find { it.name == subDirectoryName }?.delete()
        return rootDocumentFile.createDirectory(subDirectoryName)
    }

    /**
     * Extracts the Google Drive world file
     */
    fun downloadWorldFromDrive(worldName: String) {
        val context = getApplication<Application>().applicationContext

        val driveFile = DriveFile(name = worldName)
        if (googleDrive?.fileExists(driveFile) == true) {
            googleDrive?.readFileBytes(driveFile)?.let {
                recreateSubDirectoryIfNecessary(worldName)?.let { subFolder ->
                    DocumentFileZip(context, subFolder).unZip(it)
                }
            }
        }
    }
}