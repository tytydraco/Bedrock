package com.draco.bedrock.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.draco.bedrock.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class HelpHelper(private val activity: Activity) {
    val safHelpDialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.saf_help_dialog_title)
        .setMessage(R.string.saf_help_dialog_message)
        .setPositiveButton(R.string.dialog_button_okay) { _, _ -> }
        .setNeutralButton(R.string.dialog_button_support) { _, _ ->
            sendSupportEmail()
        }
        .create()

    /**
     * Open email app for support
     */
    fun sendSupportEmail() {
        val url = activity.getString(R.string.contact_url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(
                activity.window.decorView,
                R.string.snackbar_intent_failed,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
}