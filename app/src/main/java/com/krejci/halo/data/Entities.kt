package com.krejci.halo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "samples", primaryKeys = ["metric", "epoch"])
data class SampleEntity(
    val metric: String,
    val epoch: Long,
    val value: Int,
    /** "app" readings use the phone clock and are never replaced by a ring-history sync. */
    @ColumnInfo(defaultValue = "'ring'") val source: String = SOURCE_RING,
)

const val SOURCE_RING = "ring"
const val SOURCE_APP = "app"

@Entity(tableName = "sleep", primaryKeys = ["epoch"])
data class SleepEntity(val epoch: Long, val stage: Int, val durationMin: Int)

@Entity(tableName = "known_rings", primaryKeys = ["mac"])
data class KnownRingEntity(val mac: String, val name: String, val lastSeen: Long)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val startEpoch: Long,
    val endEpoch: Long,
    val avgHr: Int,
    val maxHr: Int,
    val minHr: Int,
    val samples: Int,
    /** The session's live-HR readings, comma-separated, for the detail chart. */
    val samplesCsv: String = "",
)
