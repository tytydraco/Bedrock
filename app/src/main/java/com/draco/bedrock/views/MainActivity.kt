package com.draco.bedrock.views

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var loadingDialog: AlertDialog
    private lateinit var needAccessDialog: AlertDialog
    private lateinit var badFolderDialog: AlertDialog
    private lateinit var signInFailed: AlertDialog
    private lateinit var driveAccessFailed: AlertDialog

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

        setupDialogs()
        setupObservables()
        setupGoogle()

        viewModel.prepareRecycler(this, binding.worldList)

        /* Handle pull to refresh */
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.updateWorldsList()
        }

        if (viewModel.getPersistableUri() == null)
            needAccessDialog.show()

        viewModel.piracyCheck(this)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.upload_all -> {
                viewModel.createConfirmDialog(this, R.string.confirm_dialog_upload_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.uploadAll()
                    }
                }
                true
            }
            R.id.download_all -> {
                viewModel.createConfirmDialog(this, R.string.confirm_dialog_download_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.downloadAll()
                    }
                }
                true
            }
            R.id.delete_device_all -> {
                viewModel.createConfirmDialog(this, R.string.confirm_dialog_delete_device_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.deleteAllDevice()
                    }
                }
                true
            }
            R.id.delete_cloud_all -> {
                viewModel.createConfirmDialog(this, R.string.confirm_dialog_delete_cloud_all_message) {
                    viewModel.safeCatch(binding.worldList) {
                        viewModel.deleteAllCloud()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Setup Google sign-in stuff
     */
    private fun setupGoogle() {
        viewModel.setupGoogle(this) {
            signInFailed.show()
        }

        if (viewModel.googleAccount.account == null)
            viewModel.googleAccount.discoverAccountExplicit(explicitLoginHandler)
    }

    /**
     * Setup the ViewModel observables
     */
    private fun setupObservables() {
        /* Handle loading bars */
        viewModel.working.observe(this) {
            if (it != null) {
                loadingDialog.setMessage(getString(it))
                loadingDialog.show()
            } else {
                loadingDialog.dismiss()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        /* Handle if the world list becomes empty */
        viewModel.worldList.observe(this) {
            binding.noWorlds.visibility = if (it.isNullOrEmpty())
                View.VISIBLE
            else
                View.GONE
        }
    }

    /**
     * Setup Alert Dialogs
     */
    private fun setupDialogs() {
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.checker?.destroy()
    }
}