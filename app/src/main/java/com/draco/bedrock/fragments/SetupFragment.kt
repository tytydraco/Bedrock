package com.draco.bedrock.fragments

import androidx.fragment.app.Fragment

/**
 * Fragment with a listener for when a setup step is completed
 */
open class SetupFragment : Fragment() {
    /**
     * To be called when the user is done with this page
     */
    open lateinit var finishedListener: () -> Unit
}