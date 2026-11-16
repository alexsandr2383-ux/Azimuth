package ru.azimut.app.core.model

enum class TrackSort { DATE, NAME, DISTANCE }

/** Pure search/sort rules for the track list, kept free of UI so they can be tested. */
object TrackListOps {
    fun filterSort(items: List<TrackSummary>, query: String, sort: TrackSort): List<TrackSummary> {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) items else items.filter { it.name.lowercase().contains(q) }
        return when (sort) {
            TrackSort.DATE -> filtered.sortedByDescending { it.startedAt }
            TrackSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            TrackSort.DISTANCE -> filtered.sortedByDescending { it.distanceMeters }
        }
    }

    fun next(sort: TrackSort): TrackSort = TrackSort.entries[(sort.ordinal + 1) % TrackSort.entries.size]
}
