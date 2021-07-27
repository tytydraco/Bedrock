package com.draco.bedrock.views

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
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

    private val treeHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data!!.data!!

        if (!viewModel.takePersistableUri(uri))
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
                treeHandler.launch(intent)
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

        viewModel.prepareRecycler(this, binding.worldList)

        viewModel.working.observe(this) {
            if (it == true)
                loadingDialog.show()
            else {
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
        viewModel.googleAccount.discoverAccountExplicit(explicitLoginHandler)

        if (viewModel.getPersistableUri() == null)
            needAccessDialog.show()
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
}