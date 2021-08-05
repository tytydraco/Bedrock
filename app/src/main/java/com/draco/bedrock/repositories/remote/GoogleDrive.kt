package com.draco.bedrock.repositories.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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
 * A helper singleton for Google's horribly grotesque Google Drive API
 */
object GoogleDrive {
    /**
     * Request code to be used when requesting Google Drive permissions
     */
    const val REQUEST_CODE_CHECK_PERMISSIONS = 102

    /**
     * Google Drive space to use
     */
    const val SPACE = "appDataFolder"

    /**
     * Google Drive file parent to use
     */
    const val PARENT = "appDataFolder"

    /**
     * Fields to return when listing files
     */
    const val LIST_FILES_FIELDS = "files(id, name, description)"

    /**
     * Single drive instance
     */
    private lateinit var drive: Drive

    /**
     * Authenticate the Google Drive instance
     *
     * @param account Google account to use
     */
    fun authenticate(context: Context, account: GoogleSignInAccount, scopes: List<String> = listOf(DriveScopes.DRIVE_APPDATA)) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            scopes
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
     *
     * @return True if authenticated, false otherwise
     */
    fun isAuthenticated() = ::drive.isInitialized

    /**
     * Create a file in the Google Drive application data folder
     *
     * @param fileModel Approximate file configuration
     */
    fun create(fileModel: File) {
        val file = File()
            .setName(fileModel.name)
            .setDescription(fileModel.description)
            .setParents(listOf(PARENT))

        drive
            .files()
            .create(file)
            .execute()
    }

    /**
     * Create a file in the Google Drive application data folder if it does not yet exist
     *
     * @param fileModel Approximate file configuration
     */
    fun createIfNecessary(fileModel: File) {
        if (!exists(fileModel))
            create(fileModel)
    }

    /**
     * Delete a Google Drive file
     *
     * @param fileModel Approximate file configuration
     */
    fun delete(fileModel: File) {
        val file = find(fileModel) ?: return

        drive
            .files()
            .delete(file.id)
            .execute()
    }

    /**
     * Determine if a Drive File matches a given configuration
     *
     * @param file The Google Drive file
     * @param fileModel Approximate file configuration
     * @return True if the file id, name, or description match
     */
    fun fileMetadataMatches(file: File, fileModel: File) =
        when {
            fileModel.id != null -> file.id == fileModel.id
            fileModel.name != null -> file.name == fileModel.name
            fileModel.description != null -> file.description == fileModel.description
            else -> false
        }

    /**
     * Check if a file exists in the application data folder
     *
     * @param fileModel Approximate file configuration
     * @return The first file matching the configuration, or null if nothing is found
     */
    fun find(fileModel: File) = files().find { file ->
        fileMetadataMatches(file, fileModel)
    }

    /**
     * Check if a file exists in the application data folder, matching by either
     * file id or file name.
     *
     * @param fileModel Approximate file configuration
     * @return True if the file exists, or false if nothing is found
     */
    fun exists(fileModel: File) = find(fileModel) != null

    /**
     * Get all Google Drive files in the specified space
     *
     * @return A list of files in the Google Drive application data folder.
     */
    fun files(): List<File> {
        val files = mutableListOf<File>()

        /* Loop until we no longer have a next page token */
        var nextPageToken: String? = null
        while (true) {
            val request = drive
                .files()
                .list()
                .setPageSize(1000)
                .setSpaces(SPACE)
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
     * Write content to a DriveFile
     *
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    class Write(fileModel: File) {
        val file = find(fileModel) ?: throw FileNotFoundException()

        /**
         * Update an existing file's contents
         *
         * @param content Content to write
         */
        private fun write(content: AbstractInputStreamContent) {
            /*
             * Create a copy of the important file metadata; we can't use the old one.
             * The id field cannot be changed so it is left out.
             */
            val newFile = File()
                .setName(file.name)
                .setDescription(file.description)
                .setSpaces(file.spaces)
                .setParents(file.parents)

            drive
                .files()
                .update(file.id, newFile, content)
                .execute()
        }

        fun string(content: String) {
            val contentBytes = ByteArrayContent.fromString("text/plain", content)
            write(contentBytes)
        }

        fun bytes(content: ByteArray) {
            val contentBytes = ByteArrayContent(null, content)
            write(contentBytes)
        }

        fun file(content: java.io.File) {
            val contentBytes = FileContent(null, content)
            write(contentBytes)
        }
    }

    /**
     * Read content from a DriveFile
     *
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    class Read(fileModel: File) {
        val file = find(fileModel) ?: throw FileNotFoundException()
        private val get = drive
            .files()
            .get(file.id)

        fun string(): String = get
            .executeMedia()
            .parseAsString()

        fun bytes() = get
            .executeMedia()
            .content
            .use {
                it.readBytes()
            }

        fun inputStream(): InputStream = get
            .executeMediaAsInputStream()
    }
}