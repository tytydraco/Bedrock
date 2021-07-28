package com.draco.bedrock.views

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.draco.bedrock.R
import com.draco.bedrock.databinding.ActivityMainBinding
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.viewmodels.MainActivityViewModel
import com.github.javiersantos.piracychecker.PiracyChecker
import com.github.javiersantos.piracychecker.callback
import com.github.javiersantos.piracychecker.callbacks.AllowCallback
import com.github.javiersantos.piracychecker.callbacks.DoNotAllowCallback
import com.github.javiersantos.piracychecker.callbacks.OnErrorCallback
import com.github.javiersantos.piracychecker.enums.Display
import com.github.javiersantos.piracychecker.enums.PiracyCheckerError
import com.github.javiersantos.piracychecker.enums.PirateApp
import com.github.javiersantos.piracychecker.piracyChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var loadingDialog: AlertDialog
    private lateinit var needAccessDialog: AlertDialog
    private lateinit var badFolderDialog: AlertDialog
    private lateinit var signInFailed: AlertDialog
    private lateinit var driveAccessFailed: AlertDialog

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var checker: PiracyChecker

    private val explicitLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.googleAccount.handleExplicitSignIn(it)
    }

    private val openWorldsFolderHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data

        if (uri == null || !viewModel.takePersistableUri(uri))
            badFolderDialog.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadingDialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_progress)
            .setTitle(R.string.loading_dialog_title)
            .setMessage(R.string.loading_dialog_message)
            .setCancelable(false)
            .create()

        needAccessDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.need_access_dialog_title)
            .setMessage(R.string.need_access_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_okay) { _, _ ->
                val intent = Intent().setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                openWorldsFolderHandler.launch(intent)
            }
            .create()

        badFolderDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bad_folder_dialog_title)
            .setMessage(R.string.bad_folder_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_try_again) { _, _ ->
               needAccessDialog.show()
            }
            .create()

        signInFailed = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sign_in_failed_dialog_title)
            .setMessage(R.string.sign_in_failed_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_try_again) { _, _ ->
                viewModel.googleAccount.discoverAccountExplicit(explicitLoginHandler)
            }
            .create()

        driveAccessFailed = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drive_access_failed_dialog_title)
            .setMessage(R.string.drive_access_failed_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_try_again) { _, _ ->
                viewModel.googleDrive?.requestPermissions(this)
            }
            .create()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.updateWorldsList()
        }

        viewModel.working.observe(this) {
            if (it != null) {
                loadingDialog.setMessage(getString(it))
                loadingDialog.show()
            } else {
                loadingDialog.dismiss()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewModel.googleAccount.registerLoginHandler {
            if (it != null) {
                viewModel.initGoogleDrive()
                viewModel.googleDrive?.requestPermissionsIfNecessary(this)

                if (viewModel.googleDrive?.hasPermissions() == true)
                    viewModel.updateWorldsList()
            } else {
                signInFailed.show()
            }
        }

        viewModel.worldList.observe(this) {
            binding.noWorlds.visibility = if (it.isNullOrEmpty())
                 View.VISIBLE
            else
                View.GONE
        }

        viewModel.prepareRecycler(this, binding.worldList)

        if (viewModel.googleAccount.account == null)
            viewModel.googleAccount.discoverAccountExplicit(explicitLoginHandler)

        if (viewModel.getPersistableUri() == null)
            needAccessDialog.show()

        sharedPreferences = getSharedPreferences(getString(R.string.pref_file), Context.MODE_PRIVATE)

        checker = piracyChecker {
            enableGooglePlayLicensing("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhlxHH5KebMRvybD2yRaHk/0Vd2uxPnyUGL1J0Lz4DiTnvjDsFTre1b55akdZCThZ2M06NWyyh3/70/3mxWI4F1HlMxOGM2BHGAjWKx5IWpAKEERAxhRm/M4MnaYQxFgJUEUGm+SLi+vjoQOvERrtF5svUfAudDj/6TZyxM7N/CeohMQ2GqfMcQFh0VaYbFj55bfjgFSQ/jAFw5u7gPhoqAgMxpMCFZWXWXvt4E2gx/q4LaXAc6qq9hXkxVechk6RLYMSyUG0lWAr5iewkgVWdIejsJvy2Bp7jnBeX4vt/DQGwNuzeKNzjWXfP3jLtKs2MWcNELLYwlw55wueKbFe/wIDAQAB")
            saveResultToSharedPreferences(sharedPreferences, getString(R.string.pref_key_verified))
        }.also {
            val verified = sharedPreferences.getBoolean(getString(R.string.pref_key_verified), false)
            if (!verified)
                it.start()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleDrive.REQUEST_CODE_CHECK_PERMISSIONS) {
            if (resultCode == Activity.RESULT_OK)
                viewModel.updateWorldsList()
            else
                driveAccessFailed.show()
        }
    }

    /**
     * Create and show a dialog to confirm changes
     */
    private fun createConfirmDialog(messageResId: Int, action: () -> Unit) =
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_dialog_title)
            .setMessage(messageResId)
            .setPositiveButton(R.string.dialog_button_yes) { _, _ -> action() }
            .setNegativeButton(R.string.dialog_button_no) { _, _ -> }
            .show()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.upload_all -> {
                createConfirmDialog(R.string.confirm_dialog_upload_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.uploadAll()
                    }
                }
                true
            }
            R.id.download_all -> {
                createConfirmDialog(R.string.confirm_dialog_download_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.downloadAll()
                    }
                }
                true
            }
            R.id.delete_device_all -> {
                createConfirmDialog(R.string.confirm_dialog_delete_device_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.deleteAllDevice()
                    }
                }
                true
            }
            R.id.delete_cloud_all -> {
                createConfirmDialog(R.string.confirm_dialog_delete_cloud_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.deleteAllCloud()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        checker.destroy()
    }
}