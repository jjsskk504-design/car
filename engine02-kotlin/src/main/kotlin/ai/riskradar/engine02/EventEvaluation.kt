package ai.riskradar.engine02

import kotlin.math.max

/** Offline annotation/evaluation only. Never import this module into inference. */
data class Event(val trackId: String, val direction: Direction, val timestampMs: Long)
data class EventMetrics(
    val truePositiveEvents: Int,
    val falseAlertEpisodes: Int,
    val missedEvents: Int,
    val precision: Double?,
    val recall: Double?,
    val falseAlertsPerTrackHour: Double,
    val leadTimeS: List<Double>,
    val meanLeadTimeS: Double?,
    val exposureTrackHours: Double,
    val leadWindowMs: Long,
)

/** Alerts must already represent debounced episode ONSETS, not frame-level repeated scores. */
fun eventMetrics(
    alerts: List<Event>, events: List<Event>,
    exposureTrackHours: Double, leadWindowMs: Long = 3000,
): EventMetrics {
    require(exposureTrackHours.isFinite() && exposureTrackHours > 0)
    require(leadWindowMs > 0)
    val used = mutableSetOf<Int>()
    val leads = mutableListOf<Double>()
    for (event in events.sortedBy { it.timestampMs }) {
        val match = alerts.withIndex()
            .filter { (index, a) ->
                index !in used && a.trackId == event.trackId
                    && a.direction == event.direction
                    && a.timestampMs <= event.timestampMs
                    && event.timestampMs - a.timestampMs <= leadWindowMs
            }.minByOrNull { it.value.timestampMs }
        if (match != null) {
            used.add(match.index)
            leads.add((event.timestampMs - match.value.timestampMs) / 1000.0)
        }
    }
    val tp = used.size
    val fp = alerts.size - tp
    val fn = events.size - tp
    return EventMetrics(
        truePositiveEvents = tp,
        falseAlertEpisodes = fp,
        missedEvents = fn,
        precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else null,
        recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else null,
        falseAlertsPerTrackHour = fp / exposureTrackHours,
        leadTimeS = leads,
        meanLeadTimeS = if (tp > 0) leads.average() else null,
        exposureTrackHours = exposureTrackHours,
        leadWindowMs = leadWindowMs,
    )
}

enum class Partition { TRAIN, VALIDATION, TEST }

data class EvaluationWindow(
    val site: String,
    val recording: String,
    val driver: String,
    val startMs: Long,
    val endMs: Long,
    val partition: Partition,
)

/**
 * Fail-closed on ID leakage, intersecting recording windows and non-temporal splits.
 * IDs and timestamps must be externally verified. Local track IDs are NOT driver IDs.
 */
fun validateHoldout(windows: List<EvaluationWindow>, embargoMs: Long = 0): Boolean {
    require(embargoMs >= 0 && windows.isNotEmpty())
    val groupPartitions = mutableMapOf<String, Partition>()
    for (w in windows) {
        require(w.site.isNotBlank() && w.recording.isNotBlank()
            && w.driver.isNotBlank() && w.driver != "UNKNOWN"
            && w.startMs >= 0 && w.endMs > w.startMs)
        val groups = listOf(
            "site:" + w.site,
            "recording:" + w.site + "/" + w.recording,
            "driver:" + w.driver,
        )
        for (g in groups) {
            val old = groupPartitions.putIfAbsent(g, w.partition)
            require(old == null || old == w.partition) { "Group leakage: " + g }
        }
    }
    for ((i, a) in windows.withIndex()) {
        for (b in windows.drop(i + 1)) {
            if (a.site == b.site && a.recording == b.recording) {
                val maxStart = max(a.startMs, b.startMs)
                val minEnd = minOf(a.endMs, b.endMs)
                require(maxStart - minEnd >= embargoMs) {
                    "Overlapping recording windows or embargo violation"
                }
            }
            if (a.partition.ordinal < b.partition.ordinal) {
                require(a.endMs <= b.startMs - embargoMs) { "Non-temporal holdout" }
            }
            if (b.partition.ordinal < a.partition.ordinal) {
                require(b.endMs <= a.startMs - embargoMs) { "Non-temporal holdout" }
            }
        }
    }
    return true
}
