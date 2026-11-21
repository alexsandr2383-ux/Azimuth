package ru.azimut.app.core.dem

import kotlinx.serialization.Serializable

enum class DemImportResult { OK, DUPLICATE, BAD_FILE, NO_SPACE, IO_ERROR }

/** A DEM download source; the built-ins are public Copernicus mirrors with no token. */
@Serializable
data class DemSourceDef(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    /**
     * Placeholders: {tile}=N44_00_E038_00 (Copernicus), {tileShort}=N44E038 (SRTM-style),
     * {lat}=44, {lon}=038, {ns}=N, {ew}=E. Empty = user-entered template in settings.
     */
    val urlTemplate: String,
    val note: String = "",
) {
    fun title(lang: String): String = if (lang == "en") titleEn else titleRu
}

object DemSources {
    val builtIn = listOf(
        DemSourceDef(
            "glo30",
            "Copernicus GLO-30 (~30 m)",
            "Copernicus GLO-30 (~30 m)",
            "https://copernicus-dem-30m.s3.amazonaws.com/Copernicus_DSM_COG_10_{tile}_DEM/Copernicus_DSM_COG_10_{tile}_DEM.tif",
            "? ESA/Copernicus",
        ),
        DemSourceDef(
            "glo90",
            "Copernicus GLO-90 (~90 m)",
            "Copernicus GLO-90 (~90 m)",
            "https://copernicus-dem-90m.s3.amazonaws.com/Copernicus_DSM_COG_30_{tile}_DEM/Copernicus_DSM_COG_30_{tile}_DEM.tif",
            "? ESA/Copernicus",
        ),
    )

    fun byId(id: String): DemSourceDef? = builtIn.firstOrNull { it.id == id }

    /** Expands tile placeholders for a 1? tile SW corner label like N44E038. */
    fun urlFor(def: DemSourceDef, tileShort: String): String? {
        val tpl = def.urlTemplate.takeIf { it.isNotBlank() } ?: return null
        val m = Regex("([NS])(\\d{1,2})([EW])(\\d{1,3})").matchEntire(tileShort.uppercase())
        val (ns, lat, ew, lon) = if (m != null) {
            listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
        } else {
            listOf("N", "00", "E", "000")
        }
        val lat2 = lat.padStart(2, '0')
        val lon3 = lon.padStart(3, '0')
        val copernicusTile = "${ns}${lat2}_00_${ew}${lon3}_00"
        return tpl
            .replace("{tile}", copernicusTile)
            .replace("{tileShort}", "${ns}${lat2}${ew}${lon3}")
            .replace("{lat}", lat2)
            .replace("{lon}", lon3)
            .replace("{ns}", ns)
            .replace("{ew}", ew)
    }
}
