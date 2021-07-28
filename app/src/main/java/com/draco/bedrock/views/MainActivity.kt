package com.draco.bedrock.views

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
import com.draco.bedrock.viewmodels.MainActivityViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var loadingDialog: AlertDialog
    private lateinit var helpDialog: AlertDialog

    private val setupGoogleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setupGoogle()
    }

    private val setupSafLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setupSaf()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDialogs()
        setupObservables()
        setupGoogle()
        setupSaf()

        viewModel.prepareRecycler(this, binding.worldList)

        /* Handle pull to refresh */
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.updateWorldsList()
        }

        binding.help.setOnClickListener {
            helpDialog.show()
        }

        viewModel.piracyCheck(this)
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
     * Setup file access
     */
    private fun setupSaf() {
        if (viewModel.rootDocumentFile != null)
            return

        if (viewModel.getPersistableUri())
            viewModel.updateWorldsList()
        else {
            val intent = Intent(this, SetupSafActivity::class.java)
            setupSafLauncher.launch(intent)
        }
    }

    /**
     * Setup Google sign-in stuff
     */
    private fun setupGoogle() {
        if (viewModel.googleDrive != null)
            return

        viewModel.setupGoogle(
            {
                viewModel.updateWorldsList()
            },
            {
                val intent = Intent(this, SetupGoogleActivity::class.java)
                setupGoogleLauncher.launch(intent)
            }
        )
    }

    /**
     * Setup the ViewModel observables
     */
    private fun setupObservables() {
        /* Handle loading bars */
        viewModel.working.observe(this) {
            if (it != null) {
                viewModel.setWakelock(true, window)
                loadingDialog.setMessage(getString(it))
                loadingDialog.show()
            } else {
                loadingDialog.dismiss()
                binding.swipeRefresh.isRefreshing = false
                viewModel.setWakelock(false, window)
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
            .setCancelable(false)
            .create()
        helpDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_dialog_title)
            .setMessage(R.string.help_dialog_message)
            .setPositiveButton(R.string.dialog_button_okay) { _, _ -> }
            .setNeutralButton(R.string.dialog_button_support) { _, _ ->
                viewModel.openEmail(this, binding.root)
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