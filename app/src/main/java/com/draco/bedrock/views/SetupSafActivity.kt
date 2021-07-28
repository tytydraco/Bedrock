package com.draco.bedrock.views

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.draco.bedrock.R
import com.draco.bedrock.databinding.ActivitySetupSafBinding
import com.draco.bedrock.utils.HelpHelper
import com.draco.bedrock.viewmodels.SetupSafActivityViewModel
import com.google.android.material.snackbar.Snackbar

class SetupSafActivity : AppCompatActivity() {
    private val viewModel: SetupSafActivityViewModel by viewModels()
    private lateinit var binding: ActivitySetupSafBinding

    private lateinit var helpHelper: HelpHelper

    private val openWorldsFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data

        if (uri != null && viewModel.takePersistableUri(uri))
            finish()
        else
            Snackbar.make(binding.root, R.string.snackbar_bad_folder, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupSafBinding.inflate(layoutInflater)
        setContentView(binding.root)

        helpHelper = HelpHelper(this)

        binding.grant.setOnClickListener {
            val intent = Intent().setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
            openWorldsFolderLauncher.launch(intent)
        }

        binding.help.setOnClickListener {
            helpHelper.safHelpDialog.show()
        }
    }

    override fun onBackPressed() {}
}