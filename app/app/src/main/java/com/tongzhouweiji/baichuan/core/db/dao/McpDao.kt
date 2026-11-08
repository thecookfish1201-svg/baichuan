package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tongzhouweiji.baichuan.core.db.entity.McpCallLogEntity
import com.tongzhouweiji.baichuan.core.db.entity.McpServerEntity
import com.tongzhouweiji.baichuan.core.db.entity.McpToolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface McpDao {
    @Query("SELECT * FROM mcp_servers ORDER BY createdAt DESC")
    fun observeServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun findServer(id: Long): McpServerEntity?

    @Insert
    suspend fun insertServer(server: McpServerEntity): Long

    @Update
    suspend fun updateServer(server: McpServerEntity)

    @Delete
    suspend fun deleteServer(server: McpServerEntity)

    @Query("SELECT * FROM mcp_tools WHERE serverId = :serverId ORDER BY name")
    fun observeTools(serverId: Long): Flow<List<McpToolEntity>>

    @Insert
    suspend fun insertTools(tools: List<McpToolEntity>)

    @Query("DELETE FROM mcp_tools WHERE serverId = :serverId")
    suspend fun clearTools(serverId: Long)

    @Insert
    suspend fun insertLog(log: McpCallLogEntity): Long

    @Query("SELECT * FROM mcp_call_logs ORDER BY createdAt DESC LIMIT 100")
    fun observeLogs(): Flow<List<McpCallLogEntity>>
}
