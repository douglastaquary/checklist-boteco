package com.checklistboteco.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class WorkClockType(val displayName: String) {
    ENTRADA("Entrada"),
    ALMOCO_INICIO("Saída para almoço"),
    ALMOCO_FIM("Retorno do almoço"),
    DESCANSO_INICIO("Início do descanso"),
    DESCANSO_FIM("Fim do descanso"),
    SAIDA("Saída");

    companion object {
        fun fromString(value: String): WorkClockType = entries.find { it.name == value } ?: ENTRADA
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class WorkClockEntry(
    val id: Long,
    val userId: Long,
    val type: WorkClockType,
    val registeredAt: Long,
    val location: GeoPoint,
    val distanceFromWorkMeters: Double,
    val isLate: Boolean
)

data class WorkClockSummary(
    val workedMillis: Long,
    val lunchMillis: Long,
    val restMillis: Long,
    val requiredBreakMillis: Long,
    val missingBreakMillis: Long,
    val breakOverageMillis: Long,
    val missingDailyMillis: Long,
    val missingWeeklyMillis: Long,
    val overtimeMillis: Long,
    val requiresTwoHoursRest: Boolean
)

object WorksiteLocation {
    private var cached: WorksiteInfo? = null

    val defaultInfo = WorksiteInfo(
        name = "Beco da Praia",
        latitude = -23.85491,
        longitude = -46.13872,
        radiusMeters = 5.0
    )

    fun applyCached(info: WorksiteInfo) {
        cached = info
    }

    val current: WorksiteInfo
        get() = cached ?: defaultInfo

    val name: String
        get() = current.name

    const val address = "Av. Vicente de Carvalho, 761 Centro - Bertioga"

    val allowedRadiusMeters: Double
        get() = current.radiusMeters

    val point: GeoPoint
        get() = current.point
}

data class WorksiteInfo(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double
) {
    val point = GeoPoint(latitude = latitude, longitude = longitude)
}

object WorkClockCalculator {
    const val dailyExpectedMillis: Long = 8L * 60L * 60L * 1000L
    const val weeklyExpectedMillis: Long = 40L * 60L * 60L * 1000L
    const val regularBreakMillis: Long = 60L * 60L * 1000L
    const val extendedShiftBreakMillis: Long = 2L * 60L * 60L * 1000L

    fun nextType(entries: List<WorkClockEntry>): WorkClockType {
        return when (entries.lastOrNull()?.type) {
            null, WorkClockType.SAIDA -> WorkClockType.ENTRADA
            WorkClockType.ENTRADA -> WorkClockType.ALMOCO_INICIO
            WorkClockType.ALMOCO_INICIO -> WorkClockType.ALMOCO_FIM
            WorkClockType.ALMOCO_FIM -> WorkClockType.DESCANSO_INICIO
            WorkClockType.DESCANSO_INICIO -> WorkClockType.DESCANSO_FIM
            WorkClockType.DESCANSO_FIM -> WorkClockType.SAIDA
        }
    }

    fun isLateEntry(): Boolean {
        return false
    }

    fun summarizeDay(entries: List<WorkClockEntry>, weeklyWorkedMillis: Long = entries.workedMillis()): WorkClockSummary {
        val worked = entries.workedMillis()
        val lunch = entries.durationBetween(WorkClockType.ALMOCO_INICIO, WorkClockType.ALMOCO_FIM)
        val rest = entries.durationBetween(WorkClockType.DESCANSO_INICIO, WorkClockType.DESCANSO_FIM)
        val breakMillis = lunch + rest
        val requiredBreak = if (worked >= 12L * 60L * 60L * 1000L) extendedShiftBreakMillis else regularBreakMillis
        val overtime = max(0L, weeklyWorkedMillis - weeklyExpectedMillis)
        return WorkClockSummary(
            workedMillis = worked,
            lunchMillis = lunch,
            restMillis = rest,
            requiredBreakMillis = requiredBreak,
            missingBreakMillis = if (entries.isEmpty()) 0L else max(0L, requiredBreak - breakMillis),
            breakOverageMillis = if (entries.isEmpty()) 0L else max(0L, breakMillis - requiredBreak),
            missingDailyMillis = if (entries.isEmpty()) 0L else max(0L, dailyExpectedMillis - worked),
            missingWeeklyMillis = max(0L, weeklyExpectedMillis - weeklyWorkedMillis),
            overtimeMillis = overtime,
            requiresTwoHoursRest = worked >= 12L * 60L * 60L * 1000L && breakMillis < extendedShiftBreakMillis
        )
    }

    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val earthRadiusMeters = 6_371_000.0
        val latDistance = (to.latitude - from.latitude).toRadians()
        val lonDistance = (to.longitude - from.longitude).toRadians()
        val startLat = from.latitude.toRadians()
        val endLat = to.latitude.toRadians()
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
            cos(startLat) * cos(endLat) * sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return "${hours}h ${minutes.toString().padStart(2, '0')}min"
    }

    private fun List<WorkClockEntry>.workedMillis(): Long {
        return sortedBy { it.registeredAt }.durationAcross(
            startTypes = setOf(WorkClockType.ENTRADA, WorkClockType.ALMOCO_FIM, WorkClockType.DESCANSO_FIM),
            stopTypes = setOf(WorkClockType.ALMOCO_INICIO, WorkClockType.DESCANSO_INICIO, WorkClockType.SAIDA)
        )
    }

    private fun List<WorkClockEntry>.durationBetween(startType: WorkClockType, stopType: WorkClockType): Long {
        return sortedBy { it.registeredAt }.durationAcross(setOf(startType), setOf(stopType))
    }

    private fun List<WorkClockEntry>.durationAcross(
        startTypes: Set<WorkClockType>,
        stopTypes: Set<WorkClockType>
    ): Long {
        var currentStart: Long? = null
        var total = 0L
        forEach { entry ->
            if (entry.type in startTypes) currentStart = entry.registeredAt
            if (entry.type in stopTypes && currentStart != null) {
                total += max(0L, entry.registeredAt - currentStart!!)
                currentStart = null
            }
        }
        return total
    }
}

fun Long.toLocalDate(timeZone: TimeZone): LocalDate {
    return Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date
}

private fun Double.toRadians(): Double = this * PI / 180.0
