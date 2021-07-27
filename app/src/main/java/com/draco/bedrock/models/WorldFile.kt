package com.draco.bedrock.models

import com.draco.bedrock.repositories.constants.WorldFileType

data class WorldFile(
    var name: String,
    var id: String,
    var type: WorldFileType
)