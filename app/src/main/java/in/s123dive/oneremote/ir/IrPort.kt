package `in`.s123dive.oneremote.ir

/** Thin abstraction over `ConsumerIrManager` so the transmitter is testable without Android. */
interface IrPort {
    val capability: IrCapability
    /** Blocks for the pattern duration. Throws on HAL failure. */
    fun transmit(carrierHz: Int, pattern: IntArray)
}
