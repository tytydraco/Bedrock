package com.draco.bedrock.models

import com.draco.bedrock.repositories.constants.WorldFileTypes

data class WorldFile(
    var name: String,
    var id: String,
    var type: WorldFileTypes
)