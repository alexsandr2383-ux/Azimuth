package ru.azimut.app

import ru.azimut.app.core.model.TrackSummary
import ru.azimut.app.core.storage.TrashPolicy
import ru.azimut.app.core.storage.TrashedTrack
import kotlin.test.Test
import kotlin.test.assertEquals

class TrashPolicyTest {
    private fun item(id: String, deletedAt: Long) = TrashedTrack(
        TrackSummary(id = id, name = id, profile = "foot", startedAt = 0, distanceMeters = 1.0, pointCount = 2),
        deletedAt,
    )

    @Test
    fun expiresOnlyAfterRetention() {
        val now = TrashPolicy.RETENTION_MS * 2
        val fresh = item("fresh", now - 1000)
        val edge = item("edge", now - TrashPolicy.RETENTION_MS)
        val old = item("old", now - TrashPolicy.RETENTION_MS - 1)
        assertEquals(listOf(old), TrashPolicy.expired(listOf(fresh, edge, old), now))
    }

    @Test
    fun addPutsNewestFirstAndDeduplicates() {
        val a = item("a", 1)
        val b = item("b", 2)
        val a2 = item("a", 3)
        assertEquals(listOf(a2, b), TrashPolicy.add(listOf(a, b), a2))
    }
}
