package com.draco.bedrock.repositories.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions


/**
 * A helper class to login to Google Play Services
 */
class GoogleAccount(private val context: Context) {
    private var loginHandler: ((GoogleSignInAccount?) -> Unit)? = null

    /**
     * Called when we succeed or fail to login; returns account or null
     *
     * @param handler Runnable to call with a nullable account variable
     */
    fun registerLoginHandler(handler: (GoogleSignInAccount?) -> Unit) {
        loginHandler = handler
    }

    /**
     * Unregister the login handler
     */
    fun unregisterLoginHandler() {
        loginHandler = null
    }

    /**
     * The current Google account
     */
    var account: GoogleSignInAccount? = null
        private set

    /**
     * Dig for Google account in explicit sign in intent.
     * To be used with registerForActivityResult.
     *
     * @param activityResult Result to parse given by the calling ComponentActivity
     */
    fun handleExplicitSignIn(activityResult: ActivityResult) {
        val result = Auth.GoogleSignInApi.getSignInResultFromIntent(activityResult.data)

        if (result?.isSuccess == true && result.signInAccount != null) {
            account = result.signInAccount
            loginHandler?.invoke(account)
            Log.d("GoogleAccount", "Signed in explicitly")
        } else {
            loginHandler?.invoke(null)
            Log.e("GoogleAccount", "Failed to sign in: ${result!!.status.statusMessage} ${result.status.statusCode}")
        }
    }

    /**
     * Internal method to discover accounts; if explicit result launcher is specified, user will be
     * prompted.
     *
     * @param resultLauncher Result launcher to be called if we need to handle an explicit sign in
     */
    private fun discoverAccount(resultLauncher: ActivityResultLauncher<Intent>? = null) {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)

        /* Prefer to use our currently signed in account */
        if (lastSignedInAccount != null) {
            account = lastSignedInAccount
            loginHandler?.invoke(account)
            Log.d("GoogleAccount", "Signed in with last account")
            return
        }

        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        /* First attempt to silently login; if we fail, do it explicitly */
        GoogleSignIn.getClient(context, googleSignInOptions).also { client ->
            client
                .silentSignIn()
                .addOnSuccessListener {
                    account = it
                    loginHandler?.invoke(account)
                    Log.d("GoogleAccount", "Signed in silently")
                }
                .addOnFailureListener {
                    /* NOTE: A failure here may indicate a Google Cloud issue */
                    Log.w("GoogleAccount", "Silent sign in failed")

                    if (resultLauncher != null)
                        resultLauncher.launch(client.signInIntent)
                    else
                        loginHandler?.invoke(null)
                }
        }
    }

    /**
     * Attempt to sign in to the Google account silently. Account variable will be set if an
     * account is found.
     *
     * @see handleExplicitSignIn
     */
    fun discoverAccountImplicit() = discoverAccount()

    /**
     * Attempt to sign in to the Google account, asking explicitly if necessary. Account variable
     * will be set if an account is found.
     *
     * @param explicitSignInResultLauncher A registered activity result launcher to handle
     * @see handleExplicitSignIn
     */
    fun discoverAccountExplicit(explicitSignInResultLauncher: ActivityResultLauncher<Intent>) =
        discoverAccount(explicitSignInResultLauncher)
}