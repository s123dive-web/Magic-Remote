package `in`.s123dive.oneremote.ir

/** Inclusive carrier range reported by the IR HAL. */
data class CarrierRange(val minHz: Int, val maxHz: Int) {
    operator fun contains(hz: Int) = hz in minHz..maxHz
    override fun toString() = if (minHz == maxHz) "${minHz / 1000.0} kHz" else "${minHz / 1000.0}–${maxHz / 1000.0} kHz"
}

/**
 * What this phone's IR blaster can do, detected once at launch.
 *
 * Some HALs report no ranges even though they transmit fine; in that case
 * [rangesKnown] is false and [supports] is optimistic so the carrier test can
 * give the real answer.
 */
data class IrCapability(
    val hasIr: Boolean,
    val ranges: List<CarrierRange>,
    val rangesKnown: Boolean = ranges.isNotEmpty(),
) {
    fun supports(hz: Int): Boolean = hasIr && (!rangesKnown || ranges.any { hz in it })
    val supports38k: Boolean get() = supports(NEC_CARRIER_HZ)
    val supports40k: Boolean get() = supports(SIRC_CARRIER_HZ)

    fun describeRanges(): String = when {
        !hasIr -> "none"
        !rangesKnown -> "unknown (HAL did not report)"
        else -> ranges.joinToString(", ")
    }

    companion object {
        const val NEC_CARRIER_HZ = 38_000
        const val SIRC_CARRIER_HZ = 40_000
        val NONE = IrCapability(hasIr = false, ranges = emptyList(), rangesKnown = true)
    }
}
