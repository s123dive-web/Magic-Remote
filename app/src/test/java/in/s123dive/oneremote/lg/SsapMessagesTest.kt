package `in`.s123dive.oneremote.lg

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsapMessagesTest {
    @Test
    fun registerCarriesManifestPermissionsAndKey() {
        val text = SsapMessages.register("my-key")
        val o = SsapMessages.json.parseToJsonElement(text).jsonObject
        assertEquals("register", o["type"]!!.jsonPrimitive.content)
        assertEquals("register_0", o["id"]!!.jsonPrimitive.content)
        val payload = o["payload"]!!.jsonObject
        assertEquals("my-key", payload["client-key"]!!.jsonPrimitive.content)
        assertEquals("PROMPT", payload["pairingType"]!!.jsonPrimitive.content)
        val perms = payload["manifest"]!!.jsonObject["permissions"]!!.jsonArray.map { it.jsonPrimitive.content }
        for (p in listOf("CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_TV", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "LAUNCH", "READ_INPUT_DEVICE_LIST", "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST", "READ_INSTALLED_APPS")) {
            assertTrue("permission $p", p in perms)
        }
        assertFalse(SsapMessages.json.parseToJsonElement(SsapMessages.register(null)).jsonObject["payload"]!!.jsonObject.containsKey("client-key"))
    }

    @Test
    fun requestAndParse() {
        val text = SsapMessages.request("req_1", "ssap://audio/volumeUp", SsapMessages.obj("x" to 1))
        val o = SsapMessages.json.parseToJsonElement(text).jsonObject
        assertEquals("request", o["type"]!!.jsonPrimitive.content)
        assertEquals("ssap://audio/volumeUp", o["uri"]!!.jsonPrimitive.content)
        assertEquals("1", o["payload"]!!.jsonObject["x"]!!.jsonPrimitive.content)

        val ok = SsapMessages.parse("""{"type":"response","id":"req_1","payload":{"returnValue":true,"volume":12}}""")!!
        assertEquals(true, ok.returnValue); assertEquals("req_1", ok.id)
        val err = SsapMessages.parse("""{"type":"error","id":"req_2","error":"401 insufficient permissions"}""")!!
        assertEquals("401 insufficient permissions", err.errorText)
        val prompt = SsapMessages.parse("""{"type":"response","id":"register_0","payload":{"pairingType":"PROMPT","returnValue":true}}""")!!
        assertTrue(prompt.pairingPrompt)
        val reg = SsapMessages.parse("""{"type":"registered","id":"register_0","payload":{"client-key":"abc"}}""")!!
        assertEquals("abc", reg.clientKey)
        assertNull(SsapMessages.parse("not json"))
    }

    @Test
    fun pointerMessagesAreNewlineSeparatedWithBlankTerminator() {
        assertEquals("type:button\nname:HOME\n\n", SsapMessages.pointerButton("HOME"))
        assertEquals("type:move\ndx:3\ndy:-4\ndown:0\n\n", SsapMessages.pointerMove(3, -4))
        assertEquals("type:click\n\n", SsapMessages.pointerClick())
        assertEquals("type:scroll\ndx:0\ndy:5\n\n", SsapMessages.pointerScroll(0, 5))
    }
}
