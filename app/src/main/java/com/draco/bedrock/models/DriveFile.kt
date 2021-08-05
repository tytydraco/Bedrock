package com.draco.bedrock.models

/**
 * Basic holder used when creating a Google Drive file with the GoogleDrive class
 *
 * @see com.draco.bedrock.repositories.remote.GoogleDrive
 */
data class DriveFile(
    var name: String? = null,
    var description: String? = null,
    var mimeType: String? = null,
    var id: String? = null,
    var space: String? = null,
    var parents: List<String>? = null
)