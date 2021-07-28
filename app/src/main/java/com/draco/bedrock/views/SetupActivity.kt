package com.draco.bedrock.views

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.draco.bedrock.databinding.ActivitySetupBinding
import com.draco.bedrock.fragments.setup.SetupGoogleFragment
import com.draco.bedrock.fragments.setup.SetupSafFragment
import com.draco.bedrock.viewmodels.SetupActivityViewModel

class SetupActivity : FragmentActivity() {
    private val viewModel: SetupActivityViewModel by viewModels()
    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras ?: return
        viewModel.setupPager(
            this,
            binding.pager,
            extras
        )
    }

    /**
     * Disgustingly (but necessarily) propagate activity results to fragments
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        for (fragment in supportFragmentManager.fragments)
            fragment.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() {}
}