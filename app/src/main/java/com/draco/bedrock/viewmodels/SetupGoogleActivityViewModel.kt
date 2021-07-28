package com.draco.bedrock.viewmodels

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.draco.bedrock.repositories.remote.GoogleAccount
import com.draco.bedrock.repositories.remote.GoogleDrive

class SetupGoogleActivityViewModel(application: Application) : AndroidViewModel(application) {
    val googleAccount = GoogleAccount(application.applicationContext)
    var googleDrive: GoogleDrive? = null

    /**
     * Setup Google sign-in stuff
     * @param activity Activity to request permissions on
     * @param error Runnable to execute if sign-in fails
     */
    fun setupGoogle(activity: Activity, error: (() -> Unit)?) {
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
     * Initialize the Google Drive instance
     */
    fun initGoogleDrive() {
        val context = getApplication<Application>().applicationContext

        googleAccount.account?.let {
            googleDrive = GoogleDrive(context, it)
        }
    }
}