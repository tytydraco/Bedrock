package com.draco.bedrock.repositories.remote

import android.content.Context
import com.draco.bedrock.models.DriveFile
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
     * Fields to return when listing files
     */
    const val LIST_FILES_FIELDS = "files/id,files/kind,files/mimeType,files/name,files/description"

    object Spaces {
        const val APP_DATA_FOLDER = "appDataFolder"
        const val DRIVE = "drive"
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
     * Generate one valid Google Drive file id
     *
     * @param space The Google Drive space to use
     * @return A valid Google Drive file id string
     */
    private fun generateId(space: String) = drive
        .files()
        .generateIds()
        .setSpace(space)
        .setCount(1)
        .execute()
        .ids[0]

    /**
     * Create a file in the Google Drive application data folder
     *
     * @param driveFile Approximate file configuration
     * @return Newly created file id (generated if not given) or null if invalid
     */
    fun create(driveFile: DriveFile): String? {
        val space = driveFile.space ?: return null

        /* Generate a file id */
        val fileId = driveFile.id ?: generateId(space)

        val file = File()
            .setName(driveFile.name)
            .setDescription(driveFile.description)
            .setId(fileId)
            .setMimeType(driveFile.mimeType)

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
    fun createIfNecessary(driveFile: DriveFile): String? {
        if (!exists(driveFile))
            return create(driveFile)
        return driveFile.id
    }

    /**
     * Delete a Google Drive file
     *
     * @param driveFile Approximate file configuration
     */
    fun delete(driveFile: DriveFile) {
        val file = find(driveFile) ?: return

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
    private fun matchesDriveFile(file: File, driveFile: DriveFile) =
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
    fun find(driveFile: DriveFile) = driveFile.space?.let {
        files(it)
            .find { file ->
                matchesDriveFile(file, driveFile)
            }
    }

    /**
     * Check if a file exists in the application data folder, matching by either
     * file id or file name.
     *
     * @param driveFile Approximate file configuration
     * @return True if the file exists, or false if nothing is found
     */
    fun exists(driveFile: DriveFile) = find(driveFile) != null

    /**
     * Get all Google Drive files in the specified space
     *
     * @param space The Google Drive space to use
     * @return A list of files in the Google Drive application data folder.
     */
    fun files(space: String): List<File> {
        val files = mutableListOf<File>()

        /* Loop until we no longer have a next page token */
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
     * Write content to a DriveFile
     *
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    class Write(driveFile: DriveFile) {
        val file = find(driveFile) ?: throw FileNotFoundException()

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
                .setMimeType(file.mimeType)
                .setParents(file.parents)

            drive
                .files()
                .update(file.id, newFile, content)
                .execute()
        }

        fun string(content: String) {
            val contentBytes = ByteArrayContent.fromString(file.mimeType, content)
            write(contentBytes)
        }

        fun bytes(content: ByteArray) {
            val contentBytes = ByteArrayContent(file.mimeType, content)
            write(contentBytes)
        }

        fun file(content: java.io.File) {
            val contentBytes = FileContent(file.mimeType, content)
            write(contentBytes)
        }
    }

    /**
     * Read content from a DriveFile
     *
     * @throws FileNotFoundException Desired file does not exist or cannot be found.
     */
    class Read(driveFile: DriveFile) {
        val file = find(driveFile) ?: throw FileNotFoundException()
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