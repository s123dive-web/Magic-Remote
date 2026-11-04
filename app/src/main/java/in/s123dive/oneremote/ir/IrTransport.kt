package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.Transport
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.core.capability
import `in`.s123dive.oneremote.ir.codesets.Codeset
import kotlinx.coroutines.delay

/**
 * Sends [Command]s over IR using one [Codeset]. Channel(n) expands to digits (+ OK) with the
 * codeset's inter-digit delay. PowerOn/PowerOff fall back to the Power toggle when the codeset
 * has no discrete codes (best effort, and the payload says so). Raw(bytes) is expert mode:
 * `[addr, cmd]` for NEC or `[addrHi, addrLo, cmd]` for extended NEC.
 */
class IrTransport(
    val codeset: Codeset,
    private val transmitter: IrTransmitter,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) : Transport {
    override val kind = TransportKind.IR

    override val capabilities: Set<Capability> = buildSet {
        codeset.commands.forEach { add(it.capability) }
        if (Command.Power.key in codeset.codes) add(Capability.POWER)
        if (codeset.commands.any { it is Command.Digit }) add(Capability.CHANNEL) // Channel(n) via digits
        add(Capability.RAW)
    }

    override fun supports(cmd: Command): Boolean = resolve(cmd) != null || cmd is Command.Channel && hasDigits() || cmd is Command.Raw

    private fun hasDigits() = (0..9).all { codeset.code(Command.Digit(it)) != null }

    private fun resolve(cmd: Command): IrCode? = codeset.code(cmd) ?: when (cmd) {
        Command.PowerOn, Command.PowerOff -> codeset.code(Command.Power)
        else -> null
    }

    override fun describe(cmd: Command): String = when (cmd) {
        is Command.Channel -> "digits ${cmd.n}${if (codeset.okAfterChannel) "+OK" else ""} @${codeset.digitDelayMs}ms"
        is Command.Raw -> rawCode(cmd)?.describe() ?: "RAW invalid"
        else -> {
            val code = resolve(cmd)
            val note = if (codeset.code(cmd) == null && code != null) " (toggle)" else ""
            (code?.describe() ?: "no code") + note
        }
    }

    override suspend fun send(cmd: Command): SendResult {
        if (!transmitter.capability.supports(codeset.carrierHz)) {
            return SendResult.Failed(describe(cmd), "${codeset.carrierHz / 1000} kHz not supported by this blaster")
        }
        return when (cmd) {
            is Command.Channel -> sendChannel(cmd.n)
            is Command.Raw -> {
                val code = rawCode(cmd) ?: return SendResult.Unsupported("raw needs 2 bytes (addr,cmd) or 3 (addrHi,addrLo,cmd)")
                transmitter.send(code.carrierHz, code.pattern(), code.describe())
            }
            else -> {
                val code = resolve(cmd) ?: return SendResult.Unsupported("${codeset.name} has no code for ${cmd.key}")
                transmitter.send(code.carrierHz, code.pattern(), describe(cmd))
            }
        }
    }

    private suspend fun sendChannel(n: Int): SendResult {
        if (!hasDigits()) return SendResult.Unsupported("${codeset.name} has no digit codes")
        val digits = n.toString().map { it - '0' }
        var last: SendResult = SendResult.Unsupported("empty")
        for ((i, d) in digits.withIndex()) {
            val code = codeset.code(Command.Digit(d))!!
            last = transmitter.send(code.carrierHz, code.pattern(), code.describe())
            if (last !is SendResult.Ok) return last
            if (i < digits.lastIndex || codeset.okAfterChannel) delayFn(codeset.digitDelayMs.toLong())
        }
        if (codeset.okAfterChannel) {
            val ok = codeset.code(Command.Ok) ?: return SendResult.Ok(describe(Command.Channel(n)) + " (no OK code)")
            last = transmitter.send(ok.carrierHz, ok.pattern(), ok.describe())
            if (last !is SendResult.Ok) return last
        }
        return SendResult.Ok(describe(Command.Channel(n)))
    }

    private fun rawCode(cmd: Command.Raw): IrCode? {
        val b = cmd.bytes.map { it.toInt() and 0xFF }
        return when (b.size) {
            2 -> IrCode.Nec(b[0], b[1], extended = false)
            3 -> IrCode.Nec((b[0] shl 8) or b[1], b[2], extended = true)
            else -> null
        }
    }

    override suspend fun isReachable(): Boolean = transmitter.capability.supports(codeset.carrierHz)
}
