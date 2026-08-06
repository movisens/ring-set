package com.krejci.halo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RingDao {
    /**
     * History syncs are additive. IGNORE is intentional: an existing phone-clocked app reading at
     * the same metric/second wins permanently, and repeated ring syncs do not rewrite local data.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRingSamples(list: List<SampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSamples(list: List<SampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(list: List<SleepEntity>)

    @Query("SELECT * FROM samples WHERE metric = :m ORDER BY epoch")
    fun samples(m: String): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE metric = :m ORDER BY epoch")
    suspend fun samplesNow(m: String): List<SampleEntity>

    @Query("SELECT COUNT(*) FROM samples WHERE metric = :m")
    fun count(m: String): Flow<Int>

    @Query("SELECT MAX(epoch) FROM samples WHERE metric = :m")
    suspend fun newestEpoch(m: String): Long?

    @Query("SELECT * FROM sleep ORDER BY epoch")
    fun sleep(): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep ORDER BY epoch")
    suspend fun sleepNow(): List<SleepEntity>

    @Query("SELECT COUNT(*) FROM sleep")
    fun sleepCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRing(r: KnownRingEntity)

    @Query("SELECT * FROM known_rings ORDER BY lastSeen DESC")
    fun rings(): Flow<List<KnownRingEntity>>

    @Query("SELECT * FROM known_rings ORDER BY lastSeen DESC")
    suspend fun ringsNow(): List<KnownRingEntity>

    @Insert
    suspend fun insertWorkout(w: WorkoutEntity): Long

    @Query("SELECT * FROM workouts ORDER BY startEpoch DESC")
    fun workouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startEpoch DESC")
    suspend fun workoutsNow(): List<WorkoutEntity>
}
