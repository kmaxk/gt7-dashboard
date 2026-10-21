package de.bavaco.gt7.session

import de.bavaco.gt7.storage.Driver

/** Whom the active driver races against: their own best lap or another driver's. */
sealed interface Opponent {
    data object Self : Opponent
    data class Of(val driver: Driver) : Opponent
}

/** The reference lap shown live: whose it is, its time, car and samples. */
data class ReferenceInfo(
    val owner: Driver,
    val isSelf: Boolean,
    val timeMs: Int,
    val carCode: Int,
    val drivenAtMs: Long,
    val sameCar: Boolean,
    val samples: List<LapSample>,
)
