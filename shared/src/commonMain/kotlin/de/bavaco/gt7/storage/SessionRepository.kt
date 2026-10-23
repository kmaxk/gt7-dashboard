package de.bavaco.gt7.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.bavaco.gt7.db.Gt7Database
import de.bavaco.gt7.session.CompletedLap
import de.bavaco.gt7.session.LapSample
import de.bavaco.gt7.session.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SessionSummary(
    val id: Long, val trackId: Long?, val trackName: String?, val carCode: Int,
    val startedAtMs: Long, val bestLapMs: Int?, val driverId: Long?, val driverName: String?,
    val lapCount: Int = 0, val endedAtMs: Long? = null, val name: String? = null,
)

data class LapSummary(
    val id: Long, val sessionId: Long, val number: Int, val timeMs: Int, val fuelUsed: Float, val isValid: Boolean,
    val isReference: Boolean = false,
)

enum class ComparisonBasis { REFERENCE, SAME_CAR, SAME_DRIVER, ANY }

/** The lap the race engineer compares with, and why it was chosen. */
data class ComparisonLap(val lapId: Long, val timeMs: Int, val driverName: String?, val basis: ComparisonBasis)

/** Fastest valid lap of a driver on a track: GT7 lap time, samples, car and when its session started. */
data class BestLap(val timeMs: Int, val samples: List<LapSample>, val carCode: Int, val drivenAtMs: Long, val isReference: Boolean = false)

data class LeaderboardEntry(val driver: Driver, val timeMs: Int, val carCode: Int, val drivenAtMs: Long)

/** A track for the management tab; [lineLapId] null means the line is the fastest valid lap. */
data class TrackSummary(val id: Long, val name: String, val lapCount: Int, val lastDrivenAtMs: Long?, val lineLapId: Long?)

/** A valid lap that can be chosen as a track's line. */
data class LineCandidate(val lapId: Long, val timeMs: Int, val driverName: String?, val drivenAtMs: Long)

/** A car that appears in sessions: its laps and when its last session started. */
data class DrivenCar(val carCode: Int, val lapCount: Int, val lastDrivenAtMs: Long)

class SessionRepository(private val db: Gt7Database) {
    private val q = db.gt7Queries

    fun drivers(): List<Driver> = q.allDrivers().executeAsList().map { Driver(it.id, it.name) }

    /** The first driver; creates "Fahrer 1" in an empty database. */
    fun ensureDefaultDriver(): Driver = db.transactionWithResult {
        drivers().firstOrNull() ?: Driver(createDriver(DEFAULT_DRIVER_NAME), DEFAULT_DRIVER_NAME)
    }

    fun createDriver(name: String): Long = db.transactionWithResult {
        q.insertDriver(name)
        q.lastInsertId().executeAsOne()
    }

    fun renameDriver(id: Long, name: String) = q.renameDriver(name, id)

    /** Deletes [id] together with its sessions, laps and samples. */
    fun deleteDriver(id: Long) = db.transaction {
        q.deleteSamplesOfDriver(id)
        q.deleteLapsOfDriver(id)
        q.deleteSessionsOfDriver(id)
        q.deleteDriver(id)
    }

    fun driversWithSessions(): Set<Long> = q.driversWithSessions().executeAsList().toSet()

    fun createSession(driverId: Long, carCode: Int, startedAtMs: Long): Long = db.transactionWithResult {
        q.insertSession(driverId, carCode.toLong(), startedAtMs)
        q.lastInsertId().executeAsOne()
    }

    fun endSession(id: Long, endedAtMs: Long) = q.endSession(endedAtMs, id)

    fun saveLap(sessionId: Long, lap: CompletedLap): Long = db.transactionWithResult {
        q.insertLap(sessionId, lap.number.toLong(), lap.timeMs.toLong(), lap.measuredMs.toLong(), lap.fuelUsed.toDouble(), if (lap.isValid) 1L else 0L)
        val lapId = q.lastInsertId().executeAsOne()
        for (s in lap.samples) {
            q.insertSample(
                lapId, s.timeMs.toLong(), s.distanceM.toDouble(), s.speedMps.toDouble(), s.throttle.toDouble(),
                s.brake.toDouble(), s.gear.toLong(), s.rpm.toDouble(), s.x.toDouble(), s.z.toDouble(), s.boostBar.toDouble(),
                s.steeringRad?.toDouble(), s.yawRate?.toDouble(), s.latAccel?.toDouble(), s.longAccel?.toDouble(),
                s.slipFront?.toDouble(), s.slipRear?.toDouble(),
            )
        }
        lapId
    }

    fun createTrack(name: String, outline: FloatArray): Long = db.transactionWithResult {
        q.insertTrack(name, outline.toBlob())
        q.lastInsertId().executeAsOne()
    }

    fun assignTrack(sessionId: Long, trackId: Long) = q.assignTrack(trackId, sessionId)

    fun tracks(): List<Track> = q.allTracks().executeAsList().map { Track(it.id, it.name, it.outline.toFloats()) }

    fun lastSessionStartForTrack(trackId: Long): Long? = q.lastSessionStartForTrack(trackId).executeAsOne().MAX

    /** The reference lap of [driverId] on [trackId] (any car), else the fastest valid lap in [carCode], else with any car. */
    fun bestLap(trackId: Long, driverId: Long, carCode: Int): BestLap? {
        q.referenceLapForDriver(trackId, driverId).executeAsOneOrNull()?.let {
            return BestLap(it.time_ms.toInt(), samples(it.id), it.car_code.toInt(), it.started_at_ms, isReference = true)
        }
        q.bestLapForDriverAndCar(trackId, driverId, carCode.toLong()).executeAsOneOrNull()?.let {
            return BestLap(it.time_ms.toInt(), samples(it.id), it.car_code.toInt(), it.started_at_ms)
        }
        return q.bestLapForDriver(trackId, driverId).executeAsOneOrNull()?.let {
            BestLap(it.time_ms.toInt(), samples(it.id), it.car_code.toInt(), it.started_at_ms)
        }
    }

    /** Fastest valid lap on [trackId] other than [excludeLapId]: the driver's reference lap first, then same driver and car, then same driver, then anyone. */
    fun comparisonLap(trackId: Long, driverId: Long?, carCode: Int, excludeLapId: Long): ComparisonLap? =
        q.comparisonLap(driverId, carCode.toLong(), trackId, excludeLapId).executeAsOneOrNull()?.let {
            val basis = when (it.rank) { 3L -> ComparisonBasis.REFERENCE; 2L -> ComparisonBasis.SAME_CAR; 1L -> ComparisonBasis.SAME_DRIVER; else -> ComparisonBasis.ANY }
            ComparisonLap(it.id, it.time_ms.toInt(), it.driver_name, basis)
        }

    /** Samples of the track's line: the chosen lap, else the fastest valid lap on [trackId] by anyone. */
    fun trackLine(trackId: Long): List<LapSample>? =
        q.trackLineLap(trackId).executeAsOneOrNull()?.let { samples(it) }

    fun trackSummaries(): List<TrackSummary> = q.trackSummaries().executeAsList().map {
        TrackSummary(it.id, it.name, it.lap_count.toInt(), it.last_driven_ms, it.line_lap_id)
    }

    fun lineCandidates(trackId: Long): List<LineCandidate> = q.lineCandidates(trackId).executeAsList().map {
        LineCandidate(it.id, it.time_ms.toInt(), it.driver_name, it.started_at_ms)
    }

    fun renameTrack(id: Long, name: String) = q.renameTrack(name, id)

    /** [lapId] null returns to the fastest valid lap. */
    fun setTrackLine(trackId: Long, lapId: Long?) = q.setTrackLineLap(lapId, trackId)

    /** Deletes the track; its sessions and laps stay, without a track. */
    fun deleteTrack(id: Long) = db.transaction {
        q.unassignTrack(id)
        q.deleteTrack(id)
    }

    fun leaderboard(trackId: Long, carCode: Int?): List<LeaderboardEntry> =
        q.leaderboard(trackId, carCode?.toLong()).executeAsList().map {
            LeaderboardEntry(Driver(it.driver_id, it.driver_name), it.time_ms.toInt(), it.car_code.toInt(), it.started_at_ms)
        }

    fun carsOnTrack(trackId: Long): List<Int> = q.carsOnTrack(trackId).executeAsList().map { it.toInt() }

    fun sessions(): Flow<List<SessionSummary>> =
        q.sessionSummaries().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map {
                SessionSummary(
                    it.id, it.track_id, it.track_name, it.car_code.toInt(), it.started_at_ms, it.best_lap_ms?.toInt(), it.driver_id, it.driver_name,
                    it.lap_count.toInt(), it.ended_at_ms, it.name,
                )
            }
        }

    fun laps(sessionId: Long): List<LapSummary> = q.lapsForSession(sessionId).executeAsList().map {
        LapSummary(it.id, it.session_id, it.number.toInt(), it.time_ms.toInt(), it.fuel_used.toFloat(), it.is_valid != 0L, it.is_reference != 0L)
    }

    /** A blank [name] removes the name. */
    fun renameSession(id: Long, name: String?) = q.renameSession(name?.trim()?.ifEmpty { null }, id)

    /** Removes the session with its laps and their samples. */
    fun deleteSession(id: Long) = db.transaction {
        q.deleteSamplesOfSession(id)
        q.deleteLapsOfSession(id)
        q.deleteSession(id)
    }

    /** Marks [lapId] as its driver's reference lap on its track ([on]), unmarking the previous one; or unmarks it. */
    fun setReferenceLap(lapId: Long, on: Boolean) = db.transaction {
        if (on) q.clearReferenceLike(lapId)
        q.setLapReference(if (on) 1L else 0L, lapId)
    }

    /** Whether [driverId] has a reference lap on [trackId]. */
    fun hasReferenceLap(trackId: Long, driverId: Long): Boolean = q.referenceLapForDriver(trackId, driverId).executeAsOneOrNull() != null

    fun samples(lapId: Long): List<LapSample> = q.samplesForLap(lapId).executeAsList().map {
        LapSample(
            it.time_ms.toInt(), it.distance_m.toFloat(), it.speed_mps.toFloat(), it.throttle.toFloat(), it.brake.toFloat(),
            it.gear.toInt(), it.rpm.toFloat(), it.x.toFloat(), it.z.toFloat(), it.boost_bar.toFloat(),
            it.steering_rad?.toFloat(), it.yaw_rate?.toFloat(), it.lat_accel?.toFloat(), it.long_accel?.toFloat(),
            it.slip_front?.toFloat(), it.slip_rear?.toFloat(),
        )
    }

    /** Own car names by car code. */
    fun carNames(): Map<Int, String> = q.allCarNames().executeAsList().associate { it.car_code.toInt() to it.name }

    fun setCarName(carCode: Int, name: String) = q.upsertCarName(carCode.toLong(), name)

    /** Removes the own name; the car shows its list name again. */
    fun resetCarName(carCode: Int) = q.deleteCarName(carCode.toLong())

    fun drivenCars(): List<DrivenCar> = q.drivenCars().executeAsList().map {
        DrivenCar(it.car_code.toInt(), it.lap_count.toInt(), it.last_driven_ms ?: 0L)
    }

    fun setting(key: String): String? = q.selectSetting(key).executeAsOneOrNull()

    fun setSetting(key: String, value: String) = q.upsertSetting(key, value)
}

private fun FloatArray.toBlob(): ByteArray {
    val out = ByteArray(size * 4)
    for (i in indices) {
        val bits = this[i].toBits()
        for (b in 0 until 4) out[i * 4 + b] = (bits ushr (8 * b)).toByte()
    }
    return out
}

internal fun ByteArray.toFloats(): FloatArray = FloatArray(size / 4) { i ->
    var bits = 0
    for (b in 0 until 4) bits = bits or ((this[i * 4 + b].toInt() and 0xFF) shl (8 * b))
    Float.fromBits(bits)
}
