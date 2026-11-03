package `in`.s123dive.oneremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager

/** Real `ConsumerIrManager` backing for [IrPort]. Detection happens once, at construction. */
class AndroidIrPort(context: Context) : IrPort {
    private val manager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    override val capability: IrCapability = detect()

    private fun detect(): IrCapability {
        val m = manager ?: return IrCapability.NONE
        val has = try { m.hasIrEmitter() } catch (_: Exception) { false }
        if (!has) return IrCapability.NONE
        val ranges = try {
            m.carrierFrequencies?.map { CarrierRange(it.minFrequency, it.maxFrequency) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return IrCapability(hasIr = true, ranges = ranges, rangesKnown = ranges.isNotEmpty())
    }

    override fun transmit(carrierHz: Int, pattern: IntArray) {
        val m = manager ?: throw IllegalStateException("no ConsumerIrManager")
        m.transmit(carrierHz, pattern)
    }
}
