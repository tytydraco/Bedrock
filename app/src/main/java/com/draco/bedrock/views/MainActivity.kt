package com.draco.bedrock.views

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.draco.bedrock.R
import com.draco.bedrock.databinding.ActivityMainBinding
import com.draco.bedrock.viewmodels.MainActivityViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    private val explicitLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.googleAccount.handleExplicitSignIn(it)
    }

    private val treeHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data!!.data!!
        val selectedFolder = DocumentFile.fromTreeUri(this, uri)!!

        if (viewModel.isDocumentMinecraftWorldsFolder(selectedFolder)) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            sharedPreferences
                .edit()
                .putString(getString(R.string.pref_key_uri), uri.toString())
                .apply()

            viewModel.rootDocumentFile = DocumentFile.fromTreeUri(this, uri)!!
            viewModel.updateWorldsList()
        } else {
            Toast.makeText(this, "BAD FOLDER!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(getString(R.string.pref_name), Context.MODE_PRIVATE)
        viewModel.prepareRecycler(this, binding.worldList)

        viewModel.googleAccount.registerLoginHandler {
            viewModel.initGoogleDrive()
            viewModel.googleDrive?.requestPermissionsIfNecessary(this)
        }
        viewModel.googleAccount.discoverAccountExplicit(explicitLoginHandler)

        binding.saf.setOnClickListener {
            val intent = Intent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
            treeHandler.launch(intent)
        }

        viewModel.getPersistedUri()?.let {
            viewModel.rootDocumentFile = DocumentFile.fromTreeUri(this, it)!!
            viewModel.updateWorldsList()
        }
    }
}