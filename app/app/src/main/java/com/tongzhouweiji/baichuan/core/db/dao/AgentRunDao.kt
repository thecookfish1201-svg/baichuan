package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.AgentActionEntity
import com.tongzhouweiji.baichuan.core.db.entity.AgentConsentEntity
import com.tongzhouweiji.baichuan.core.db.entity.AgentRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRunDao {
    @Query("SELECT * FROM agent_consents WHERE agentId = :agentId ORDER BY acceptedAt DESC LIMIT 1")
    suspend fun findConsent(agentId: Long): AgentConsentEntity?

    @Query("DELETE FROM agent_consents WHERE agentId = :agentId")
    suspend fun deleteConsent(agentId: Long)

    @Query("SELECT * FROM agent_consents ORDER BY acceptedAt DESC LIMIT 1")
    suspend fun latestConsent(): AgentConsentEntity?

    @Insert
    suspend fun insertConsent(consent: AgentConsentEntity): Long

    @Query("SELECT * FROM agent_runs ORDER BY startedAt DESC LIMIT 50")
    fun observeRuns(): Flow<List<AgentRunEntity>>

    @Insert
    suspend fun insertRun(run: AgentRunEntity): Long

    @Query("UPDATE agent_runs SET state = :state, endedAt = :endedAt, cancelReason = :reason WHERE id = :id")
    suspend fun finishRun(id: Long, state: String, endedAt: Long, reason: String?)

    @Insert
    suspend fun insertAction(action: AgentActionEntity): Long

    @Query("SELECT * FROM agent_actions WHERE runId = :runId ORDER BY createdAt ASC")
    fun observeActions(runId: Long): Flow<List<AgentActionEntity>>
}
