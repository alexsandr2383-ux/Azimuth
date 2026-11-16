package ru.azimut.app

import ru.azimut.app.core.model.TrackListOps
import ru.azimut.app.core.model.TrackSort
import ru.azimut.app.core.model.TrackSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackListOpsTest {
    private fun t(id: String, name: String, started: Long, dist: Double) =
        TrackSummary(id = id, name = name, profile = "foot", startedAt = started, distanceMeters = dist, pointCount = 2)

    private val list = listOf(
        t("a", "Беркут", 100, 5000.0),
        t("b", "азимут", 300, 1000.0),
        t("c", "Вечерний", 200, 9000.0),
    )

    @Test
    fun sortsByDateNewestFirst() =
        assertEquals(listOf("b", "c", "a"), TrackListOps.filterSort(list, "", TrackSort.DATE).map { it.id })

    @Test
    fun sortsByNameIgnoringCase() =
        assertEquals(listOf("b", "a", "c"), TrackListOps.filterSort(list, "", TrackSort.NAME).map { it.id })

    @Test
    fun sortsByDistanceLongestFirst() =
        assertEquals(listOf("c", "a", "b"), TrackListOps.filterSort(list, "", TrackSort.DISTANCE).map { it.id })

    @Test
    fun filtersByNameIgnoringCaseAndSpaces() =
        assertEquals(listOf("b"), TrackListOps.filterSort(list, "  АЗИ ", TrackSort.DATE).map { it.id })

    @Test
    fun queryMatchesSubstring() =
        assertEquals(listOf("b"), TrackListOps.filterSort(list, "зим", TrackSort.DATE).map { it.id })

    @Test
    fun nextCyclesThroughAllModes() {
        assertEquals(TrackSort.NAME, TrackListOps.next(TrackSort.DATE))
        assertEquals(TrackSort.DATE, TrackListOps.next(TrackSort.DISTANCE))
    }
}
