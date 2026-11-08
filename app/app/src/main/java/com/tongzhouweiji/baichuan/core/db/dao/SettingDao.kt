package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.tongzhouweiji.baichuan.core.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Query("SELECT * FROM `settings`")
    suspend fun listAll(): List<SettingEntity>

    @Query("DELETE FROM `settings`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<SettingEntity>)
}
