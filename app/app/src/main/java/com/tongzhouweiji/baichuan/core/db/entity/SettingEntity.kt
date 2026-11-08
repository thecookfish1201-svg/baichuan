package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
@Serializable
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
