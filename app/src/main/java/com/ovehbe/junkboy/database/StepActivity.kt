package com.ovehbe.junkboy.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Lifetime activity per rule, kept outside [FilteredMessage] so retention cannot erase the
 * evidence that stale-rule auto-pause depends on.
 */
@Entity(tableName = "step_activity")
data class StepActivity(
    @PrimaryKey val step: String,
    val lastEnforcedHit: Long,
    val enforcedHits: Int
)

@Dao
interface StepActivityDao {

    @Query("SELECT * FROM step_activity")
    fun getAll(): Flow<List<StepActivity>>

    @Query("SELECT * FROM step_activity WHERE step = :step")
    suspend fun get(step: String): StepActivity?

    @Query("INSERT INTO step_activity (step, lastEnforcedHit, enforcedHits) VALUES (:step, :now, 1) " +
        "ON CONFLICT(step) DO UPDATE SET lastEnforcedHit = :now, enforcedHits = enforcedHits + 1")
    suspend fun recordHit(step: String, now: Long)

    @Query("DELETE FROM step_activity")
    suspend fun clear()

    @Query("DELETE FROM step_activity WHERE step = :step")
    suspend fun delete(step: String)
}
