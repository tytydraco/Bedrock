package com.draco.bedrock.views

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.draco.bedrock.R
import com.draco.bedrock.databinding.ActivityMainBinding
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.viewmodels.MainActivityViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private val googleAccount: GoogleAccount by lazy { GoogleAccount(this) }

    private val explicitLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        googleAccount.handleExplicitSignIn(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.googleSignIn.setOnClickListener {
            googleAccount.discoverAccountExplicit(explicitLoginHandler)
        }

        binding.googleDriveSignIn.setOnClickListener {
            val googleDrive = GoogleDrive(this, googleAccount.account!!)
            googleDrive.requestPermissionsIfNecessary(this)
        }
    }
}