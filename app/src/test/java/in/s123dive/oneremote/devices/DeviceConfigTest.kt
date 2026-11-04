package `in`.s123dive.oneremote.devices

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.data.AppStateJson
import `in`.s123dive.oneremote.ir.IrCode
import `in`.s123dive.oneremote.ir.codesets.AirtelCodesets
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceConfigTest {
    @Test
    fun typedAccessorsAndWith() {
        val c = DeviceConfig.empty().with(DeviceConfig.IP, "10.0.0.5").with(DeviceConfig.SECURE, true).with(DeviceConfig.DIGIT_DELAY_MS, 400)
        assertEquals("10.0.0.5", c.ip); assertEquals(true, c.secure); assertEquals(400, c.digitDelayMs)
        assertNull(c.psk); assertNull(c.with(DeviceConfig.IP, null).ip)
        assertNull(c.without(DeviceConfig.IP).ip)
    }

    @Test
    fun customCodesRoundTripThroughJson() {
        val codes = mapOf(Command.Power.key to IrCode.Nec(0x10, 0x20, true), Command.Ok.key to IrCode.Raw(38000, listOf(900, 450, 560), "ok"))
        val c = DeviceConfig.empty().withCustomCodes(codes)
        val text = AppStateJson.json.encodeToString(JsonObject.serializer(), c.json)
        val back = DeviceConfig(AppStateJson.json.decodeFromString(JsonObject.serializer(), text))
        assertEquals(codes, back.customCodes)
    }

    @Test
    fun effectiveCodesetOverlaysCustomCodes() {
        val c = DeviceConfig.empty().withCustomCodes(mapOf(Command.Power.key to IrCode.Nec(1, 2))).with(DeviceConfig.DIGIT_DELAY_MS, 500)
        val eff = c.effectiveCodeset(AirtelCodesets.stb2, "box", DeviceType.AIRTEL_HD)!!
        assertEquals(IrCode.Nec(1, 2), eff.code(Command.Power))
        assertEquals(AirtelCodesets.stb2.code(Command.Ok), eff.code(Command.Ok))
        assertEquals(500, eff.digitDelayMs)
        assertNull(DeviceConfig.empty().effectiveCodeset(null, "box", DeviceType.AIRTEL_HD))
        assertEquals("box (expert)", c.effectiveCodeset(null, "box", DeviceType.AIRTEL_HD)!!.name)
    }

    @Test
    fun irccCodes() {
        val c = DeviceConfig.empty().withIrccCodes(mapOf("Power" to "AAAAAQAAAAEAAAAVAw=="))
        assertEquals("AAAAAQAAAAEAAAAVAw==", c.irccCodes["Power"])
    }
}
