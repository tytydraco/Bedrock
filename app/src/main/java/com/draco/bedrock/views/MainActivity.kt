package com.draco.bedrock.views

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.draco.bedrock.databinding.ActivityMainBinding
import com.draco.bedrock.repositories.constants.GoogleDriveFiles
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive
import com.draco.bedrock.utils.DocumentFileZip
import com.draco.bedrock.viewmodels.MainActivityViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private val googleAccount: GoogleAccount by lazy { GoogleAccount(this) }
    private val googleDrive: GoogleDrive by lazy { GoogleDrive(this, googleAccount.account!!) }

    private lateinit var zipBytes: ByteArray

    private val explicitLoginHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        googleAccount.handleExplicitSignIn(it)
    }

    private val treeHandler = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data!!.data!!
        val documentFile = DocumentFile.fromTreeUri(this, uri)

        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val documentFileZip = DocumentFileZip(this, documentFile!!).zip()
        Log.d("DONE!", documentFileZip.size.toString())

        GlobalScope.launch {
            //googleDrive.createFileIfNecessary(GoogleDriveFiles.cloudWorldZip)
            //googleDrive.writeFileBytes(GoogleDriveFiles.cloudWorldZip, documentFileZip)

            //documentFile.listFiles().forEach { l ->
            //    l.delete()
            //}

            DocumentFileZip(this@MainActivity, documentFile).unZip(zipBytes)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.googleSignIn.setOnClickListener {
            googleAccount.discoverAccountExplicit(explicitLoginHandler)
        }

        binding.googleDriveSignIn.setOnClickListener {
            googleDrive.requestPermissionsIfNecessary(this)

            GlobalScope.launch {
                if (googleDrive.fileExists(GoogleDriveFiles.cloudWorldZip)) {
                    zipBytes = googleDrive.readFileBytes(GoogleDriveFiles.cloudWorldZip)
                    Log.d("EXISTING", zipBytes.size.toString())
                }
            }
        }

        binding.saf.setOnClickListener {
            val intent = Intent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
            treeHandler.launch(intent)
        }
    }
}