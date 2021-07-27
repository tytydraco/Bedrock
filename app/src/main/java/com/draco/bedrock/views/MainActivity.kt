package com.draco.bedrock.views

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.draco.bedrock.databinding.ActivityMainBinding
import com.draco.bedrock.viewmodels.MainActivityViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var documentFile: DocumentFile

    private val explicitLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.googleAccount.handleExplicitSignIn(it)
    }

    private val treeHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data!!.data!!
        documentFile = DocumentFile.fromTreeUri(this, uri)!!
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.googleSignIn.setOnClickListener {
            viewModel.googleAccount.discoverAccountExplicit(explicitLoginHandler)
        }

        binding.googleDriveSignIn.setOnClickListener {
            viewModel.initGoogleDrive()
            viewModel.googleDrive?.requestPermissionsIfNecessary(this)
        }

        binding.saf.setOnClickListener {
            val intent = Intent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
            treeHandler.launch(intent)
        }

        binding.upload.setOnClickListener {
            viewModel.uploadWorldsDriveFile(documentFile)
        }

        binding.download.setOnClickListener {
            viewModel.extractWorldsDriveFile(documentFile)
        }
    }
}