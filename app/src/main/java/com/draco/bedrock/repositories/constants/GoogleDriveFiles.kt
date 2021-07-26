package com.draco.bedrock.repositories.constants

import com.draco.bedrock.models.DriveFile

object GoogleDriveFiles {
    /**
     * Drive file where our worlds are stored
     */
    val cloudWorldZip = DriveFile(
        name = "cloud_world_zip",
        mimeType = "application/zip"
    )
}