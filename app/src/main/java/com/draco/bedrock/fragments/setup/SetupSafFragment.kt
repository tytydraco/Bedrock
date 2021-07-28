package com.draco.bedrock.fragments.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.draco.bedrock.R
import com.draco.bedrock.databinding.ActivitySetupSafBinding
import com.draco.bedrock.fragments.SetupFragment
import com.draco.bedrock.utils.HelpHelper
import com.draco.bedrock.utils.MinecraftWorldUtils
import com.google.android.material.snackbar.Snackbar

class SetupSafFragment : SetupFragment() {
    private lateinit var binding: ActivitySetupSafBinding
    private lateinit var minecraftWorldUtils: MinecraftWorldUtils

    private lateinit var helpHelper: HelpHelper

    private val openWorldsFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data

        if (uri != null && takePersistableUri(uri))
            finishedListener()
        else
            Snackbar.make(binding.root, R.string.snackbar_bad_folder, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Store the persistable Uri and update the root document
     * @param uri OPEN_DOCUMENT_TREE uri
     * @return True if we persisted it, false if the selected world is invalid
     */
    private fun takePersistableUri(uri: Uri): Boolean {
        val selectedFolder = DocumentFile.fromTreeUri(requireContext(), uri)!!
        if (!minecraftWorldUtils.isValidWorld(selectedFolder))
            return false

        context
            ?.contentResolver
            ?.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

        return true
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        minecraftWorldUtils = MinecraftWorldUtils(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySetupSafBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        helpHelper = HelpHelper(requireActivity())

        binding.grant.setOnClickListener {
            val intent = Intent().setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
            openWorldsFolderLauncher.launch(intent)
        }

        binding.help.setOnClickListener {
            helpHelper.safHelpDialog.show()
        }
    }
}