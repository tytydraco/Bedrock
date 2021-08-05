package com.draco.bedrock.viewmodels

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.draco.bedrock.R
import com.draco.bedrock.models.WorldFile
import com.draco.bedrock.recyclers.WorldsRecyclerAdapter
import com.draco.bedrock.repositories.constants.Minecraft
import com.draco.bedrock.repositories.constants.SetupSteps
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.utils.MinecraftWorldUtils
import com.github.javiersantos.piracychecker.PiracyChecker
import com.github.javiersantos.piracychecker.piracyChecker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val WAKELOCK_TAG = "Bedrock::Working"
    }

    private val googleAccount = GoogleAccount(application.applicationContext)
    private var worldsRecyclerAdapter: WorldsRecyclerAdapter? = null

    private val powerManager = application.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)

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

    var rootDocumentFile: DocumentFile? = null

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
     * Check which setup fragments need to be shown
     * @param completedListener A runnable that is passed a bundle with setup steps
     */
    fun getSetupActivityBundle(completedListener: (Bundle) -> Unit) {
        val rootDocumentSet = (rootDocumentFile != null)
        val googleDriveAuthenticated = GoogleDrive.isAuthenticated()

        /* The setup is already completed; don't return anything */
        if (rootDocumentSet && googleDriveAuthenticated)
            return

        val bundle = Bundle()

        /* We need file access */
        if (!rootDocumentSet) {
            if (!getPersistableUri())
                bundle.putBoolean(SetupSteps.SAF, true)
        }

        /* We need Google Drive access; delay completed listener until we can be sure */
        if (!googleDriveAuthenticated) {
            setupGoogle(
                {
                    completedListener.invoke(bundle)
                },
                {
                    bundle.putBoolean(SetupSteps.GOOGLE, true)
                    completedListener.invoke(bundle)
                }
            )
        } else {
            /* If we already have Google Drive setup, just return what we have instantly */
            completedListener.invoke(bundle)
        }
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
        }

        val verified = sharedPreferences.getBoolean(context.getString(R.string.pref_key_verified), false)
        if (!verified)
            checker?.start()
    }

    /**
     * Setup Google sign-in stuff
     * @param success Runnable to execute if sign-in succeeds
     * @param error Runnable to execute if sign-in fails
     */
    fun setupGoogle(success: (() -> Unit)?, error: (() -> Unit)?) {
        googleAccount.registerLoginHandler {
            if (it != null) {
                initGoogleDrive()

                val hasGoogleDrivePermissions = GoogleSignIn.hasPermissions(
                    it,
                    Scope(DriveScopes.DRIVE_APPDATA),
                    Scope(Scopes.EMAIL)
                )

                if (hasGoogleDrivePermissions)
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
            .find { it.uri.toString().contains(Minecraft.WORLDS_FOLDER_NAME) }
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
    fun updateWorldsList() {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_updating_world_list)

            val list = minecraftWorldUtils.list(rootDocumentFile)

            _worldList.postValue(list)

            /* Update the recycler adapter */
            withContext(Dispatchers.Main) {
                worldsRecyclerAdapter?.let {
                    it.worldFileList = list.toMutableList()
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
    private fun safeCatch(view: View, runnable: () -> Unit) {
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
        if (worldsRecyclerAdapter != null)
            return

        worldsRecyclerAdapter = WorldsRecyclerAdapter(context, mutableListOf()).apply {
            setHasStableIds(true)

            uploadHook = { view, worldId ->
                createConfirmDialog(view.context, R.string.confirm_dialog_upload_message) {
                    uploadWorldToDrive(view, worldId)
                }
            }

            downloadHook = { view, worldId ->
                createConfirmDialog(view.context, R.string.confirm_dialog_download_message) {
                    downloadWorldFromDrive(view, worldId)
                }
            }

            deleteDeviceHook = { view, worldId ->
                createConfirmDialog(view.context, R.string.confirm_dialog_delete_device_message) {
                    deleteWorldFromDevice(worldId)
                }
            }

            deleteCloudHook = { view, worldId ->
                createConfirmDialog(view.context, R.string.confirm_dialog_delete_cloud_message) {
                    deleteWorldFromDrive(view, worldId)
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
     * Initialize the Google Drive instance and authenticate it
     */
    fun initGoogleDrive() {
        val context = getApplication<Application>().applicationContext

        if (!GoogleDrive.isAuthenticated()) {
            googleAccount.account?.let {
                GoogleDrive.authenticate(context, it)
            }
        }
    }

    /**
     * Toggle wakelocks while doing work
     * @param state True to keep a wakelock, false to release it
     */
    @SuppressLint("WakelockTimeout")
    fun setWakelock(state: Boolean, window: Window) {
        when (state) {
            true -> {
                if (wakeLock.isHeld)
                    return
                wakeLock.acquire()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            false -> {
                if (!wakeLock.isHeld)
                    return
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                wakeLock.release()
            }
        }
    }

    /**
     * Upload all Minecraft worlds
     * @param view A view to display the Snackbar on
     */
    fun uploadAll(view: View) {
        val worldList = _worldList.value ?: return
        val rootDocumentFile = rootDocumentFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_uploading)
            safeCatch(view) {
                minecraftWorldUtils.uploadAll(worldList, rootDocumentFile)
            }
            _working.postValue(null)
            updateWorldsList()
        }
    }

    /**
     * Download all Minecraft worlds
     * @param view A view to display the Snackbar on
     */
    fun downloadAll(view: View) {
        val worldList = _worldList.value ?: return
        val rootDocumentFile = rootDocumentFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_downloading)
            safeCatch(view) {
                minecraftWorldUtils.downloadAll(worldList, rootDocumentFile)
            }
            _working.postValue(null)
            updateWorldsList()
        }
    }

    /**
     * Delete all local Minecraft worlds
     */
    fun deleteAllDevice() {
        val rootDocumentFile = rootDocumentFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_delete_device)
            minecraftWorldUtils.deleteAllDevice(rootDocumentFile)
            _working.postValue(null)
            updateWorldsList()
        }
    }

    /**
     * Delete all remote Minecraft worlds
     * @param view A view to display the Snackbar on
     */
    fun deleteAllCloud(view: View) {
        val worldList = _worldList.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_delete_cloud)
            safeCatch(view) {
                minecraftWorldUtils.deleteAllCloud(worldList)
            }
            _working.postValue(null)
            updateWorldsList()
        }
    }

    /**
     * Delete a local Minecraft world
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDevice(worldId: String) {
        val rootDocumentFile = rootDocumentFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_delete_device)
            minecraftWorldUtils.deleteWorldFromDevice(rootDocumentFile, worldId)
            _working.postValue(null)
            updateWorldsList()
        }
    }

    /**
     * Delete a remote Minecraft world
     * @param view A view to display the Snackbar on
     * @param worldId Folder ID to use to find what to delete
     */
    fun deleteWorldFromDrive(view: View, worldId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_delete_cloud)
            safeCatch(view) {
                minecraftWorldUtils.deleteWorldFromDrive(worldId)
            }
            _working.postValue(null)
            updateWorldsList()
        }
    }

    /**
     * Upload a Minecraft world to the cloud
     * @param view A view to display the Snackbar on
     * @param worldId Folder ID to use to find what to delete
     */
    fun uploadWorldToDrive(view: View, worldId: String) {
        val rootDocumentFile = rootDocumentFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_uploading)
            safeCatch(view) {
                minecraftWorldUtils.uploadWorldToDrive(rootDocumentFile, worldId)
            }
            _working.postValue(null)
            updateWorldsList()
        }
    }

    /**
     * Download and extract a Minecraft world from the cloud
     * @param view A view to display the Snackbar on
     * @param worldId Folder ID to use to find what to delete
     */
    fun downloadWorldFromDrive(view: View, worldId: String) {
        val rootDocumentFile = rootDocumentFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _working.postValue(R.string.working_downloading)
            safeCatch(view) {
                minecraftWorldUtils.downloadWorldFromDrive(rootDocumentFile, worldId)
            }
            _working.postValue(null)
            updateWorldsList()
        }
    }
}