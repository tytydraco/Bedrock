package com.draco.bedrock.viewmodels

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
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
import com.github.javiersantos.piracychecker.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    val googleAccount = GoogleAccount(application.applicationContext)
    var googleDrive: GoogleDrive? = null
    private var worldsRecyclerAdapter: WorldsRecyclerAdapter? = null

    private val sharedPreferences = application
        .applicationContext
        .getSharedPreferences(
            application
                .applicationContext
                .getString(R.string.pref_file),
            Context.MODE_PRIVATE
        )

    private val contentResolver = getApplication<Application>().contentResolver

    private val minecraftWorldUtils = MinecraftWorldUtils(application.applicationContext)

    private var rootDocumentFile: DocumentFile? = null

    var checker: PiracyChecker? = null

    private val _working = MutableLiveData<Int?>()
    val working: LiveData<Int?> = _working

    private val _worldList = MutableLiveData<List<WorldFile>>()
    val worldList: LiveData<List<WorldFile>> = _worldList

    init {
        /* Try to initialize the rootDocumentFile if we already granted it permissions */
        getPersistableUri()
    }

    /**
     * Start the piracy checker if it is not setup yet
     * @param activity Activity to use when showing the error
     */
    fun piracyCheck(activity: Activity) {
        if (checker != null)
            return

        val context = getApplication<Application>().applicationContext

        checker = activity.piracyChecker {
            enableGooglePlayLicensing("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhlxHH5KebMRvybD2yRaHk/0Vd2uxPnyUGL1J0Lz4DiTnvjDsFTre1b55akdZCThZ2M06NWyyh3/70/3mxWI4F1HlMxOGM2BHGAjWKx5IWpAKEERAxhRm/M4MnaYQxFgJUEUGm+SLi+vjoQOvERrtF5svUfAudDj/6TZyxM7N/CeohMQ2GqfMcQFh0VaYbFj55bfjgFSQ/jAFw5u7gPhoqAgMxpMCFZWXWXvt4E2gx/q4LaXAc6qq9hXkxVechk6RLYMSyUG0lWAr5iewkgVWdIejsJvy2Bp7jnBeX4vt/DQGwNuzeKNzjWXfP3jLtKs2MWcNELLYwlw55wueKbFe/wIDAQAB")
            saveResultToSharedPreferences(
                sharedPreferences,
                context.getString(R.string.pref_key_verified)
            )

            callback {
                onError {
                    Log.d("PiracyChecker", "Error: ${it.name}")
                }
                doNotAllow { piracyCheckerError, _ ->
                    Log.d("PiracyChecker", "Disallowed: ${piracyCheckerError.name}")
                }
                allow {
                    Log.d("PiracyChecker", "Allowed")
                }
            }
        }

        val verified = sharedPreferences.getBoolean(context.getString(R.string.pref_key_verified), false)
        if (!verified)
            checker?.start()
    }

    /**
     * Setup Google sign-in stuff
     * @param activity Activity to request permissions on
     * @param error Runnable to execute if sign-in fails
     */
    fun setupGoogle(success: (() -> Unit)?, error: (() -> Unit)?) {
        googleAccount.registerLoginHandler {
            if (it != null) {
                initGoogleDrive()

                if (googleDrive?.hasPermissions() == true)
                    success?.invoke()
                else
                    error?.invoke()
            } else
                error?.invoke()
        }
        googleAccount.discoverAccountImplicit()
    }

    /**
     * Create and show a dialog to confirm changes
     * @param context View-bound context
     * @param messageResId String Res-id to show in a dialog
     * @param action Runnable to execute if user confirms
     */
    fun createConfirmDialog(context: Context, messageResId: Int, action: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.confirm_dialog_title)
            .setMessage(messageResId)
            .setPositiveButton(R.string.dialog_button_yes) { _, _ -> action() }
            .setNegativeButton(R.string.dialog_button_no) { _, _ -> }
            .show()
    }

    /**
     * Check if the user already has SAF permissions and set the root document
     * @return True if we were able to set the root document, false if no persisted Uri
     */
    fun getPersistableUri(): Boolean {
        contentResolver
            .persistedUriPermissions
            .find { it.uri.toString().contains(MinecraftConstants.WORLDS_FOLDER_NAME) }
            ?.uri?.let {
                val context = getApplication<Application>().applicationContext
                rootDocumentFile = DocumentFile.fromTreeUri(context, it)!!
                return true
            }
        return false
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
                createConfirmDialog(view.context, R.string.confirm_dialog_upload_message) {
                    viewModelScope.launch(Dispatchers.IO) {
                        safeCatch(view) {
                            uploadWorldToDrive(worldName)
                            updateWorldsList()
                        }
                    }
                }
            }

            downloadHook = { view, worldName ->
                createConfirmDialog(view.context, R.string.confirm_dialog_download_message) {
                    viewModelScope.launch(Dispatchers.IO) {
                        safeCatch(view) {
                            downloadWorldFromDrive(worldName)
                            updateWorldsList()
                        }
                    }
                }
            }

            deleteDeviceHook = { view, worldName ->
                createConfirmDialog(view.context, R.string.confirm_dialog_delete_device_message) {
                    viewModelScope.launch(Dispatchers.IO) {
                        safeCatch(view) {
                            deleteWorldFromDevice(worldName)
                            updateWorldsList()
                        }
                    }
                }
            }

            deleteCloudHook = { view, worldName ->
                createConfirmDialog(view.context, R.string.confirm_dialog_delete_cloud_message) {
                    viewModelScope.launch(Dispatchers.IO) {
                        safeCatch(view) {
                            deleteWorldFromDrive(worldName)
                            updateWorldsList()
                        }
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
     * @param worldId Folder ID to use to find what to delete
     */
    fun uploadWorldToDrive(worldId: String) {
        _working.postValue(R.string.working_uploading)

        rootDocumentFile?.listFiles()?.find { it.name == worldId }?.let {
            val driveFile = DriveFile(
                name = worldId,
                description = minecraftWorldUtils.getLevelName(it)
            )

            _working.postValue(R.string.working_zipping)
            val zipFile = DocumentFileZip(contentResolver, it).zip()
            googleDrive?.createFileIfNecessary(driveFile)
            googleDrive?.writeFileRaw(driveFile, zipFile)
            zipFile.delete()
        }

        _working.postValue(null)
    }

    /**
     * Download and extract a Minecraft world from the cloud
     * @param worldId Folder ID to use to find what to delete
     */
    fun downloadWorldFromDrive(worldId: String) {
        _working.postValue(R.string.working_downloading)

        val driveFile = DriveFile(name = worldId)
        if (googleDrive?.fileExists(driveFile) == true) {
            googleDrive?.readFileInputStream(driveFile)?.let {
                /* Recreate any existing world folders */
                rootDocumentFile?.listFiles()?.find { it.name == worldId }?.delete()
                rootDocumentFile?.createDirectory(worldId)?.let { subFolder ->
                    _working.postValue(R.string.working_unzipping)
                    DocumentFileZip(contentResolver, subFolder).unZip(it)
                }
            }
        }

        _working.postValue(null)
    }
}