package com.draco.bedrock.views

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.draco.bedrock.R
import com.draco.bedrock.databinding.ActivitySetupGoogleBinding
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.viewmodels.SetupGoogleActivityViewModel
import com.google.android.material.snackbar.Snackbar

class SetupGoogleActivity : AppCompatActivity() {
    private val viewModel: SetupGoogleActivityViewModel by viewModels()
    private lateinit var binding: ActivitySetupGoogleBinding

    private val explicitLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.googleAccount.handleExplicitSignIn(it)
    }

    /**
     * Setup Google sign-in stuff
     */
    private fun setupGoogle() {
        viewModel.setupGoogle(this) {
            Snackbar.make(binding.root, R.string.snackbar_sign_in_failed, Snackbar.LENGTH_LONG).show()
        }
        viewModel.googleAccount.discoverAccountExplicit(explicitLoginHandler)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupGoogleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.grant.setOnClickListener {
            setupGoogle()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleDrive.REQUEST_CODE_CHECK_PERMISSIONS) {
            if (resultCode == Activity.RESULT_OK)
                finish()
            else
                Snackbar.make(binding.root, R.string.snackbar_drive_access_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {}
}