package com.draco.bedrock.repositories.remote

import android.app.Activity
import android.content.Context
import com.draco.bedrock.models.DriveFile
import com.draco.bedrock.repositories.constants.GoogleDriveSpaces
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import java.io.FileNotFoundException
import java.io.InputStream


/**
 * A helper singleton for Google's horribly grotesque Google Drive code
 */
object GoogleDrive {
    /**
     * Request code to be used when requesting Google Drive permissions
     */
    const val REQUEST_CODE_CHECK_PERMISSIONS = 102
    const val LIST_FILES_FIELDS = "files/id,files/kind,files/mimeType,files/name,files/description"

    /**
     * Google permission scopes that we need to grant
     */
    object PermissionScopes {
        val driveAppData = Scope(DriveScopes.DRIVE_APPDATA)
        val email = Scope(Scopes.EMAIL)
    }

    /**
     * Single drive instance
     */
    private lateinit var drive: Drive

    /**
     * Authenticate the Google Drive instance
     *
     * @param account Google account to use
     */
    fun authenticate(context: Context, account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
            .setSelectedAccount(account.account)

        drive = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory(),
            credential
        )
            .setApplicationName(context.packageName)
            .build()
    }

    /**
     * Check if the class instance is authenticated
     * @return True if authenticated, false otherwise
     */
    fun isAuthenticated() = ::drive.isInitialized

    /**
     * Generate one valid Google Drive file id
     *
     * @return A valid Google Drive file id string
     */
    private fun generateId(space: String = GoogleDriveSpaces.APP_DATA_FOLDER) = drive
        .files()
        .generateIds()
        .setSpace(space)
        .setCount(1)
        .execute()
        .ids[0]

    /**
     * Check if the user has Google Drive application folder permissions
     *
     * @param account Google account to use
     * @return True if we have the requested Google Drive permissions
     */
    fun hasPermissions(account: GoogleSignInAccount) = GoogleSignIn.hasPermissions(
        account,
        PermissionScopes.driveAppData,
        PermissionScopes.email
    )

    /**
     * Request permissions from the user using a request code
     *
     * @param account Google account to use
     * @param activity Activity to forward request code to
     * @see REQUEST_CODE_CHECK_PERMISSIONS
     */
    fun requestPermissions(activity: Activity, account: GoogleSignInAccount) {
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
    fun requestPermissionsIfNecessary(activity: Activity, account: GoogleSignInAccount) {
        if (!hasPermissions(account))
            requestPermissions(activity, account)
    }

    /**
     * Create a file in the Google Drive application data folder
     *
     * @param driveFile Approximate file configuration
     * @return Newly created file id (generated if not given)
     */
    fun createFile(driveFile: DriveFile): String {
        /* Generate a file id */
        val fileId = driveFile.id ?: generateId(driveFile.parent)

        val file = File()
            .setName(driveFile.name)
            .setDescription(driveFile.description)
            .setId(fileId)
            .setMimeType(driveFile.mimeType)
            .setParents(listOf(driveFile.parent))

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
     */
    fun deleteFile(driveFile: DriveFile) {
        val file = findFile(driveFile) ?: return

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
     * @return True if the file id, name, or description match
     */
    private fun fileMatchesFileConfig(file: File, driveFile: DriveFile) =
        when {
            driveFile.id != null -> file.id == driveFile.id
            driveFile.name != null -> file.name == driveFile.name
            driveFile.description != null -> file.description == driveFile.description
            else -> false
        }

    /**
     * Check if a file exists in the application data folder
     *
     * @param driveFile Approximate file configuration
     * @return The first file matching the configuration, or null if nothing is found
     */
    fun findFile(driveFile: DriveFile) = getFiles()
        .find {
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
    fun getFiles(space: String = GoogleDriveSpaces.APP_DATA_FOLDER): List<File> {
        val files = mutableListOf<File>()

        var nextPageToken: String? = null
        while (true) {
            val request = drive
                .files()
                .list()
                .setSpaces(space)
                .setFields(LIST_FILES_FIELDS)
                .execute().also {
                    nextPageToken?.let { token -> it.nextPageToken = token }
                }
            files.addAll(request.files)

            if (request.nextPageToken == null)
                break
            nextPageToken = request.nextPageToken
        }

        return files
    }

    /**
     * Write raw byte stream content to an **existing** Google Drive file.
     *
     * @param file Google Drive File
     * @param content Input stream content
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    private fun writeFile(file: File, content: AbstractInputStreamContent) {
        /*
         * Create a copy of the important file metadata; we can't use the old one.
         * The id field cannot be changed so it is left out.
         */
        val newFile = File()
            .setName(file.name)
            .setDescription(file.description)
            .setMimeType(file.mimeType)
            .setParents(file.parents)

        drive
            .files()
            .update(file.id, newFile, content)
            .execute()
    }

    /**
     * Write string content to an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @param content String to write to the file
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun writeFileString(driveFile: DriveFile, content: String) {
        val file = findFile(driveFile) ?: throw FileNotFoundException()
        val contentBytes = ByteArrayContent.fromString(file.mimeType, content)
        writeFile(file, contentBytes)
    }

    /**
     * Write byte content to an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @param content Byte array to write to the file
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun writeFileBytes(driveFile: DriveFile, content: ByteArray) {
        val file = findFile(driveFile) ?: throw FileNotFoundException()
        val contentBytes = ByteArrayContent(file.mimeType, content)
        writeFile(file, contentBytes)
    }

    /**
     * Write byte content to an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @param rawFile The raw File to upload
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun writeFileRaw(driveFile: DriveFile, rawFile: java.io.File) {
        val file = findFile(driveFile) ?: throw FileNotFoundException()
        val contentBytes = FileContent(file.mimeType, rawFile)
        writeFile(file, contentBytes)
    }

    /**
     * Read string content of an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @return The string content of the file.
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun readFileString(driveFile: DriveFile): String {
        val file = findFile(driveFile) ?: throw FileNotFoundException()

        return drive
            .files()
            .get(file.id)
            .executeMedia()
            .parseAsString()
    }

    /**
     * Read byte content of an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @return The byte content of the file.
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun readFileBytes(driveFile: DriveFile): ByteArray {
        val file = findFile(driveFile) ?: throw FileNotFoundException()

        return drive
            .files()
            .get(file.id)
            .executeMedia()
            .content
            .use {
                it.readBytes()
            }
    }

    /**
     * Read input stream of an **existing** Google Drive file.
     *
     * @param driveFile Approximate file configuration
     * @return The input stream content of the file.
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    fun readFileInputStream(driveFile: DriveFile): InputStream {
        val file = findFile(driveFile) ?: throw FileNotFoundException()

        return drive
            .files()
            .get(file.id)
            .executeMediaAsInputStream()
    }
}