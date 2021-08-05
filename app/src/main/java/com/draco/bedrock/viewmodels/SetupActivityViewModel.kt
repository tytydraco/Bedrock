package com.draco.bedrock.viewmodels

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.viewpager2.widget.ViewPager2
import com.draco.bedrock.fragments.SetupFragment
import com.draco.bedrock.fragments.adapters.SetupAdapter
import com.draco.bedrock.fragments.setup.SetupGoogleFragment
import com.draco.bedrock.fragments.setup.SetupSafFragment
import com.draco.bedrock.repositories.constants.SetupSteps

class SetupActivityViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * Initialize the ViewPager
     *
     * @param fragmentActivity The FragmentActivity holding the ViewPager
     * @param pager The ViewPager2 instance
     * @param bundle The bundle containing booleans for which setup steps to show
     */
    fun setupPager(
        fragmentActivity: FragmentActivity,
        pager: ViewPager2,
        bundle: Bundle
    ) {
        val fragments = mutableListOf<SetupFragment>()

        if (bundle.getBoolean(SetupSteps.GOOGLE))
            fragments.add(SetupGoogleFragment())
        if (bundle.getBoolean(SetupSteps.SAF))
            fragments.add(SetupSafFragment())

        /* Make all but the last fragment simply increment the current page */
        for (fragment in fragments.take(fragments.size - 1))
            fragment.finishedListener = { pager.currentItem++ }

        /* Make the last fragment finish the activity and mark our initial setup as complete */
        fragments
            .last()
            .finishedListener = { fragmentActivity.finish() }

        pager.apply {
            isUserInputEnabled = false
            adapter = SetupAdapter(fragmentActivity, fragments)
        }
    }
}