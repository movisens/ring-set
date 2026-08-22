package com.krejci.halo.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Row counts restored by [BackupService.importJson], for user feedback. */
data class RestoreSummary(val samples: Int, val sleep: Int, val rings: Int, val workouts: Int) {
    val total: Int get() = samples + sleep + rings + workouts
}

/**
 * Full-fidelity backup of everything a Halo install holds: the Room database (heart rate, steps,
 * SpO₂, stress, HRV, sleep, known rings, workouts) plus the SharedPreferences [UserProfile]. Exports
 * to / restores from a single self-describing JSON document, so data survives an uninstall/reinstall
 * (e.g. switching to a release-signed build).
 *
 * Restore is destructive-by-design: it replaces the current contents so a backup round-trips exactly.
 */
class BackupService(context: Context) {
    private val db = RingDb.get(context)
    private val dao = db.dao()
    private val profileStore = UserProfileStore(context.getSharedPreferences("halo", Context.MODE_PRIVATE))

    /** Serializes all data to a JSON string. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", "halo-backup")
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("profile", profileStore.load().toJson())

        val samples = JSONArray()
        for (s in dao.allSamplesNow()) {
            samples.put(
                JSONObject()
                    .put("metric", s.metric).put("epoch", s.epoch)
                    .put("value", s.value).put("source", s.source),
            )
        }
        root.put("samples", samples)

        val sleep = JSONArray()
        for (s in dao.sleepNow()) {
            sleep.put(JSONObject().put("epoch", s.epoch).put("stage", s.stage).put("durationMin", s.durationMin))
        }
        root.put("sleep", sleep)

        val rings = JSONArray()
        for (r in dao.ringsNow()) {
            rings.put(JSONObject().put("mac", r.mac).put("name", r.name).put("lastSeen", r.lastSeen))
        }
        root.put("rings", rings)

        val workouts = JSONArray()
        for (w in dao.workoutsNow()) {
            workouts.put(
                JSONObject()
                    .put("id", w.id).put("type", w.type)
                    .put("startEpoch", w.startEpoch).put("endEpoch", w.endEpoch)
                    .put("avgHr", w.avgHr).put("maxHr", w.maxHr).put("minHr", w.minHr)
                    .put("samples", w.samples).put("samplesCsv", w.samplesCsv),
            )
        }
        root.put("workouts", workouts)

        root.toString()
    }

    /** Replaces all current data with the contents of [text]. Throws if the document isn't a backup. */
    suspend fun importJson(text: String): RestoreSummary = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        require(root.optString("format") == "halo-backup") { "Not a Halo backup file." }

        val samples = root.optJSONArray("samples").toSamples()
        val sleep = root.optJSONArray("sleep").toSleep()
        val rings = root.optJSONArray("rings").toRings()
        val workouts = root.optJSONArray("workouts").toWorkouts()

        db.withTransaction {
            dao.clearSamples(); dao.clearSleep(); dao.clearRings(); dao.clearWorkouts()
            if (samples.isNotEmpty()) dao.insertSamples(samples)
            if (sleep.isNotEmpty()) dao.insertSleep(sleep)
            for (r in rings) dao.upsertRing(r)
            if (workouts.isNotEmpty()) dao.insertWorkouts(workouts)
        }

        root.optJSONObject("profile")?.let { profileStore.save(it.toProfile()) }

        RestoreSummary(samples.size, sleep.size, rings.size, workouts.size)
    }

    private fun UserProfile.toJson() = JSONObject()
        .put("name", name).put("birthYear", birthYear).put("sex", sex.ordinal)
        .put("heightCm", heightCm).put("weightKg", weightKg).put("restingHr", restingHr)
        .put("goalSteps", goalSteps).put("goalSleepHours", goalSleepHours.toDouble())
        .put("bedtimeMin", bedtimeMin).put("wakeMin", wakeMin)

    private fun JSONObject.toProfile() = UserProfile(
        name = optString("name", ""),
        birthYear = optInt("birthYear", 0),
        sex = Sex.entries.getOrElse(optInt("sex", 0)) { Sex.UNSPECIFIED },
        heightCm = optInt("heightCm", 0),
        weightKg = optInt("weightKg", 0),
        restingHr = optInt("restingHr", 0),
        goalSteps = optInt("goalSteps", 8000),
        goalSleepHours = optDouble("goalSleepHours", 8.0).toFloat(),
        bedtimeMin = optInt("bedtimeMin", 1380),
        wakeMin = optInt("wakeMin", 420),
    )

    private fun JSONArray?.toSamples(): List<SampleEntity> = mapObjects {
        SampleEntity(it.getString("metric"), it.getLong("epoch"), it.getInt("value"), it.optString("source", SOURCE_RING))
    }

    private fun JSONArray?.toSleep(): List<SleepEntity> = mapObjects {
        SleepEntity(it.getLong("epoch"), it.getInt("stage"), it.getInt("durationMin"))
    }

    private fun JSONArray?.toRings(): List<KnownRingEntity> = mapObjects {
        KnownRingEntity(it.getString("mac"), it.optString("name", ""), it.optLong("lastSeen", 0))
    }

    private fun JSONArray?.toWorkouts(): List<WorkoutEntity> = mapObjects {
        WorkoutEntity(
            id = it.optLong("id", 0), type = it.getString("type"),
            startEpoch = it.getLong("startEpoch"), endEpoch = it.getLong("endEpoch"),
            avgHr = it.getInt("avgHr"), maxHr = it.getInt("maxHr"), minHr = it.getInt("minHr"),
            samples = it.getInt("samples"), samplesCsv = it.optString("samplesCsv", ""),
        )
    }

    private inline fun <T> JSONArray?.mapObjects(map: (JSONObject) -> T): List<T> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}
