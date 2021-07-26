package com.draco.bedrock.repositories.remote

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.draco.bedrock.models.DriveFile
import com.draco.bedrock.repositories.constants.GoogleDriveSpaces
import java.io.FileNotFoundException


/**
 * A helper class for Google's horribly grotesque Google Drive code
 *
 * @param account A signed-in Google Account
 * @see GoogleAccount
 */
class GoogleDrive(
    context: Context,
    private val account: GoogleSignInAccount
) {
    companion object {
        /**
         * Request code to be used when requesting Google Drive permissions
         */
        const val REQUEST_CODE_CHECK_PERMISSIONS = 102

        /**
         * Google permission scopes that we need to grant
         */
        object PermissionScopes {
            val driveAppData = Scope(DriveScopes.DRIVE_APPDATA)
            val email = Scope(Scopes.EMAIL)
        }
    }

    /**
     * OAuth2 credential for the application data context
     */
    private val credential = GoogleAccountCredential.usingOAuth2(
        context,
        listOf(DriveScopes.DRIVE_APPDATA)
    )
        .setSelectedAccount(account.account)

    /**
     * Google drive instance for our application data context
     */
    private val drive = Drive.Builder(
        AndroidHttp.newCompatibleTransport(),
        GsonFactory(),
        credential
    )
        .setApplicationName(context.packageName)
        .build()

    /**
     * Generate one valid Google Drive file id
     *
     * @return A valid Google Drive file id string
     */
    private fun generateId() = drive
        .files()
        .generateIds()
        .setSpace(GoogleDriveSpaces.APP_DATA_FOLDER)
        .setCount(1)
        .execute()
        .ids[0]

    /**
     * Check if the user has Google Drive application folder permissions
     *
     * @return True if we have the requested Google Drive permissions
     */
    fun hasPermissions() = GoogleSignIn.hasPermissions(
        account,
        PermissionScopes.driveAppData,
        PermissionScopes.email
    )

    /**
     * Request permissions from the user using a request code
     *
     * @param activity Activity to forward request code to
     * @see REQUEST_CODE_CHECK_PERMISSIONS
     */
    fun requestPermissions(activity: Activity) {
        GoogleSignIn.requestPermissions(
            activity,
            REQUEST_CODE_CHECK_PERMISSIONS,
            account,
            PermissionScopes.driveAppData,
            PermissionScopes.email
        )
    }

    /**
     * If the user does not have permissions already, prompt them for it.
     *
     * @param activity Activity to forward request code to
     * @see requestPermissions
     */
    fun requestPermissionsIfNecessary(activity: Activity) {
        if (!hasPermissions())
            requestPermissions(activity)
    }

    /**
     * Create a file in the Google Drive application data folder
     *
     * @param driveFile Approximate file configuration
     * @return Newly created file id (generated if not given)
     */
    fun createFile(driveFile: DriveFile): String {
        /* Generate a file id */
        val fileId = driveFile.id ?: generateId()

        val file = File()
            .setName(driveFile.name)
            .setId(fileId)
            .setMimeType(driveFile.mimeType)
            .setParents(listOf(GoogleDriveSpaces.APP_DATA_FOLDER))

        drive
            .files()
            .create(file)
            .execute()

        return fileId
    }

    /**
     * Create a file in the Google Drive application data folder if it does not yet exist
     *
     * @param driveFile Approximate file configuration
     * @return Newly created file id, or existing one if it exists already
     */
    fun createFileIfNecessary(driveFile: DriveFile): String? {
        if (!fileExists(driveFile))
            return createFile(driveFile)
        return driveFile.id
    }

    /**
     * Delete a Google Drive file
     *
     * @param driveFile Approximate file configuration
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun deleteFile(driveFile: DriveFile) {
        val file = findFile(driveFile) ?: throw FileNotFoundException()

        drive
            .files()
            .delete(file.id)
            .execute()
    }

    /**
     * Determine if a Drive File matches a given configuration
     *
     * @param file The Google Drive file
     * @param driveFile Approximate file configuration
     * @return True if the file id or name match
     */
    private fun fileMatchesFileConfig(file: File, driveFile: DriveFile) =
        when {
            driveFile.id != null -> file.id == driveFile.id
            driveFile.name != null -> file.name == driveFile.name
            else -> false
        }

    /**
     * Check if a file exists in the application data folder
     *
     * @param driveFile Approximate file configuration
     * @return The first file matching the configuration, or null if nothing is found
     */
    fun findFile(driveFile: DriveFile) = getFiles()
        ?.find {
            fileMatchesFileConfig(it, driveFile)
        }

    /**
     * Check if a file exists in the application data folder, matching by either
     * file id or file name.
     *
     * @param driveFile Approximate file configuration
     * @return True if the file exists, or false if nothing is found
     */
    fun fileExists(driveFile: DriveFile) = findFile(driveFile) != null

    /**
     * @return A list of files in the Google Drive application data folder.
     */
    fun getFiles(): List<File>? = drive
        .files()
        .list()
        .setSpaces(GoogleDriveSpaces.APP_DATA_FOLDER)
        .execute()
        .files

    /**
     * Write content to an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @param content String to write to the file
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun writeFile(driveFile: DriveFile, content: String) {
        val file = findFile(driveFile) ?: throw FileNotFoundException()

        /* Create a content stream to use when writing the file */
        val contentBytes = ByteArrayContent.fromString(file.mimeType, content)

        /*
         * Create a copy of the important file metadata; we can't use the old one.
         * The id field cannot be changed so it is left out.
         */
        val newFile = File()
            .setName(file.name)
            .setMimeType(file.mimeType)
            .setParents(file.parents)

        drive
            .files()
            .update(file.id, newFile, contentBytes)
            .execute()
    }

    /**
     * Read content of an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @return The string content of the file.
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun readFile(driveFile: DriveFile): String {
        val file = findFile(driveFile) ?: throw FileNotFoundException()

        return drive
            .files()
            .get(file.id)
            .executeMedia()
            .parseAsString()
    }
}