package com.draco.bedrock.fragments.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.draco.bedrock.R
import com.draco.bedrock.databinding.ActivitySetupGoogleBinding
import com.draco.bedrock.fragments.SetupFragment
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.google.android.material.snackbar.Snackbar

class SetupGoogleFragment : SetupFragment() {
    private lateinit var binding: ActivitySetupGoogleBinding

    private lateinit var googleAccount: GoogleAccount
    private var googleDrive: GoogleDrive? = null

    private val explicitLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        googleAccount.handleExplicitSignIn(it)
    }

    /**
     * Setup Google sign-in stuff
     * @param activity Activity to request permissions on
     * @param error Runnable to execute if sign-in fails
     */
    private fun setupGoogle(activity: Activity, error: (() -> Unit)?) {
        googleAccount.registerLoginHandler {
            if (it != null) {
                initGoogleDrive()
                googleDrive?.requestPermissionsIfNecessary(activity)

                if (googleDrive?.hasPermissions() == true)
                    activity.finish()
            } else
                error?.invoke()
        }
    }

    /**
     * Setup Google sign-in stuff
     */
    private fun setupGoogle() {
        setupGoogle(requireActivity()) {
            Snackbar.make(binding.root, R.string.snackbar_sign_in_failed, Snackbar.LENGTH_LONG).show()
        }
        googleAccount.discoverAccountExplicit(explicitLoginHandler)
    }

    /**
     * Initialize the Google Drive instance
     */
    private fun initGoogleDrive() {
        googleAccount.account?.let {
            googleDrive = GoogleDrive(requireContext(), it)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        googleAccount = GoogleAccount(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySetupGoogleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.grant.setOnClickListener {
            setupGoogle()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleDrive.REQUEST_CODE_CHECK_PERMISSIONS) {
            if (resultCode == Activity.RESULT_OK)
                finishedListener()
            else
                Snackbar.make(binding.root, R.string.snackbar_drive_access_failed, Snackbar.LENGTH_LONG).show()
        }
    }
}