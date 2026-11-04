package `in`.s123dive.oneremote.ir.codesets

import `in`.s123dive.oneremote.core.DeviceType

/** Registry of built-in codesets. */
object Codesets {
    val all: List<Codeset> = listOf(LgNecCodeset.codeset, SonySircCodeset.addr1, SonySircCodeset.addr26) + AirtelCodesets.candidates
    private val byId = all.associateBy { it.id }

    fun byId(id: String): Codeset? = byId[id]
    fun forType(type: DeviceType): List<Codeset> = all.filter { it.type == type }

    /** What the scanner tries for a type, in order. */
    fun scanCandidates(type: DeviceType): List<Codeset> = when (type) {
        DeviceType.AIRTEL_HD -> AirtelCodesets.candidates
        DeviceType.LG_WEBOS -> listOf(LgNecCodeset.codeset)
        DeviceType.SONY_BRAVIA -> listOf(SonySircCodeset.addr1, SonySircCodeset.addr26)
    }

    fun defaultFor(type: DeviceType): Codeset = when (type) {
        DeviceType.AIRTEL_HD -> AirtelCodesets.stb2
        DeviceType.LG_WEBOS -> LgNecCodeset.codeset
        DeviceType.SONY_BRAVIA -> SonySircCodeset.addr1
    }
}
