package `in`.s123dive.oneremote.sony

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyApiTest {
    @Test
    fun rpcShape() {
        val o = SonyApi.json.parseToJsonElement(SonyApi.rpc("getSystemInformation")).jsonObject
        assertEquals("getSystemInformation", o["method"]!!.jsonPrimitive.content)
        assertEquals(0, o["params"]!!.jsonArray.size)
        assertEquals("1.0", o["version"]!!.jsonPrimitive.content)
        val p = SonyApi.json.parseToJsonElement(SonyApi.rpc("setAudioVolume", SonyApi.obj("target" to "speaker", "volume" to "+1"))).jsonObject
        assertEquals("+1", p["params"]!!.jsonArray[0].jsonObject["volume"]!!.jsonPrimitive.content)
    }

    @Test
    fun irccSoapEnvelope() {
        val s = SonyApi.irccSoap("AAAAAQAAAAEAAAAVAw==")
        assertTrue(s.contains("<IRCCCode>AAAAAQAAAAEAAAAVAw==</IRCCCode>"))
        assertTrue(s.contains("urn:schemas-sony-com:service:IRCC:1"))
        assertEquals("\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"", SonyApi.SOAP_ACTION)
    }

    @Test
    fun replyClassification() {
        assertTrue(SonyApi.parseReply("""{"result":[{"status":"active"}],"id":1}""").ok)
        val auth = SonyApi.parseReply("""{"error":[403,"Forbidden"],"id":1}""")
        assertTrue(auth.isAuthError); assertFalse(auth.ok)
        assertTrue(SonyApi.parseReply("""{"error":[40005,"Display Is Turned off"],"id":1}""").isPoweredOff)
        assertTrue(SonyApi.parseReply("""{"error":[12,"No Such Method"],"id":1}""").isUnsupported)
        assertEquals(-1, SonyApi.parseReply("<html>").errorCode)
    }

    @Test
    fun parsers() {
        val sys = SonyApi.parseSystemInformation(SonyApi.parseReply("""{"result":[{"product":"TV","model":"KDL-43W6600","generation":"3.4.1","name":"BRAVIA","macAddr":"ac:9b:0a:11:22:33","area":"IND"}],"id":1}"""))!!
        assertEquals("KDL-43W6600", sys.model); assertEquals("3.4.1", sys.generation); assertEquals("AC:9B:0A:11:22:33", sys.mac)
        val codes = SonyApi.parseRemoteControllerInfo(SonyApi.parseReply("""{"result":[{"bundled":true,"type":"RM-J1100"},[{"name":"PowerOff","value":"AAAAAQAAAAEAAAAvAw=="},{"name":"Num1","value":"AAAAAQAAAAEAAAAAAw=="}]],"id":1}"""))
        assertEquals("AAAAAQAAAAEAAAAvAw==", codes["PowerOff"]); assertEquals(2, codes.size)
        val vol = SonyApi.parseVolumeInformation(SonyApi.parseReply("""{"result":[[{"target":"speaker","volume":11,"mute":false,"maxVolume":100,"minVolume":0},{"target":"headphone","volume":5,"mute":true}]],"id":1}"""))!!
        assertEquals(11, vol.volume); assertEquals(false, vol.mute)
        assertEquals("active", SonyApi.parsePowerStatus(SonyApi.parseReply("""{"result":[{"status":"active"}],"id":1}""")))
        val apps = SonyApi.parseAppList(SonyApi.parseReply("""{"result":[[{"title":"YouTube","uri":"com.sony.dtv.youtube","icon":"http://x"}]],"id":1}"""))
        assertEquals("com.sony.dtv.youtube", apps.single().uri)
        val inputs = SonyApi.parseInputs(SonyApi.parseReply("""{"result":[[{"uri":"extInput:hdmi?port=1","title":"HDMI 1","label":"Airtel","connection":true}]],"id":1}"""))
        assertEquals("Airtel (HDMI 1)", inputs.single().title)
        assertNull(SonyApi.parseSystemInformation(SonyApi.parseReply("""{"error":[403,"x"]}""")))
    }

    @Test
    fun irccNameCandidatesCoverTheRemote() {
        for (k in listOf("POWER", "VOL_UP", "CH_UP", "OK", "BACK", "HOME", "DIGIT:0", "DIGIT:9", "PLAY", "RED")) assertTrue(k, SonyApi.IRCC_NAMES.containsKey(k))
        assertEquals(listOf("Num7"), SonyApi.IRCC_NAMES["DIGIT:7"])
    }
}
