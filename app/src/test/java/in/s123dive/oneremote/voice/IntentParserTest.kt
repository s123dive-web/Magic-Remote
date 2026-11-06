package `in`.s123dive.oneremote.voice

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.voice.VoiceIntent.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {
    private fun p(text: String) = IntentParser.parse(text)

    @Test fun numbersDigitsAndWords() {
        assertEquals(120 to 1, IntentParser.number(listOf("120"), 0))
        assertEquals(120 to 3, IntentParser.number(listOf("ek", "sau", "bees"), 0))
        assertEquals(105 to 4, IntentParser.number(listOf("one", "hundred", "and", "five"), 0))
        assertEquals(100 to 1, IntentParser.number(listOf("sau"), 0))
        assertEquals(45 to 2, IntentParser.number(listOf("forty", "five"), 0))
        assertEquals(5 to 1, IntentParser.number(listOf("पाच"), 0))
        assertEquals(220 to 3, IntentParser.number(listOf("दो", "सौ", "बीस"), 0))
        assertEquals(null, IntentParser.number(listOf("hello"), 0))
    }

    // ---- English ----
    @Test fun en_channel120() = assertEquals(VoiceIntent.Channel(120), p("channel 120"))
    @Test fun en_channelWords() = assertEquals(VoiceIntent.Channel(45), p("go to channel forty five"))
    @Test fun en_channelNumberPhrase() = assertEquals(VoiceIntent.Channel(7), p("Channel number 7 please"))
    @Test fun en_channelUp() = assertEquals(VoiceIntent.Send(Command.ChUp, Target.BOX), p("channel up"))
    @Test fun en_nextChannel() = assertEquals(VoiceIntent.Send(Command.ChUp, Target.BOX), p("next channel"))
    @Test fun en_previousChannel() = assertEquals(VoiceIntent.Send(Command.ChDown, Target.BOX), p("previous channel"))
    @Test fun en_volumeUp() = assertEquals(VoiceIntent.Send(Command.VolUp, Target.TV), p("volume up"))
    @Test fun en_turnVolumeDown() = assertEquals(VoiceIntent.Send(Command.VolDown, Target.TV), p("turn the volume down"))
    @Test fun en_louder() = assertEquals(VoiceIntent.Send(Command.VolUp, Target.TV), p("louder"))
    @Test fun en_mute() = assertEquals(VoiceIntent.Send(Command.Mute, Target.TV), p("mute"))
    @Test fun en_tvOn() = assertEquals(VoiceIntent.Send(Command.PowerOn, Target.TV), p("turn on the TV"))
    @Test fun en_tvOff() = assertEquals(VoiceIntent.Send(Command.PowerOff, Target.TV), p("switch off the television"))
    @Test fun en_boxOn() = assertEquals(VoiceIntent.Send(Command.PowerOn, Target.BOX), p("turn on the set top box"))
    @Test fun en_boxOff() = assertEquals(VoiceIntent.Send(Command.PowerOff, Target.BOX), p("Airtel box off"))
    @Test fun en_hdmi2() = assertEquals(VoiceIntent.SwitchInput(2), p("switch to HDMI 2"))
    @Test fun en_hdmiJoined() = assertEquals(VoiceIntent.SwitchInput(1), p("hdmi1"))
    @Test fun en_openYoutube() = assertEquals(VoiceIntent.OpenApp("youtube"), p("open YouTube"))
    @Test fun en_launchNetflix() = assertEquals(VoiceIntent.OpenApp("netflix"), p("launch Netflix please"))
    @Test fun en_searchCricket() = assertEquals(VoiceIntent.TypeText("cricket", true), p("search cricket"))
    @Test fun en_searchFor() = assertEquals(VoiceIntent.TypeText("ipl highlights", true), p("search for IPL highlights"))
    @Test fun en_typeText() = assertEquals(VoiceIntent.TypeText("hello world", false), p("type hello world"))
    @Test fun en_sleep30() = assertEquals(VoiceIntent.Sleep(30), p("sleep in 30 minutes"))
    @Test fun en_sleepWords() = assertEquals(VoiceIntent.Sleep(45), p("sleep timer forty five minutes"))
    @Test fun en_home() = assertEquals(VoiceIntent.Send(Command.Home, Target.ANY), p("home"))
    @Test fun en_back() = assertEquals(VoiceIntent.Send(Command.Back, Target.ANY), p("go back"))
    @Test fun en_ok() = assertEquals(VoiceIntent.Send(Command.Ok, Target.ANY), p("ok"))
    @Test fun en_select() = assertEquals(VoiceIntent.Send(Command.Ok, Target.ANY), p("select"))
    @Test fun en_pause() = assertEquals(VoiceIntent.Send(Command.Pause, Target.ANY), p("pause"))
    @Test fun en_watchTv() = assertEquals(VoiceIntent.RunMacro("Watch TV"), p("watch tv"))
    @Test fun en_allOff() = assertEquals(VoiceIntent.RunMacro("All off"), p("turn everything off"))
    @Test fun en_bareNumber() = assertEquals(VoiceIntent.Channel(9), p("9"))
    @Test fun en_unparsed() { val r = p("what is the weather"); assertTrue(r is VoiceIntent.Unparsed); assertEquals("what is the weather", (r as VoiceIntent.Unparsed).text) }
    @Test fun en_empty() = assertTrue(p("   ") is VoiceIntent.Unparsed)

    // ---- fan ----
    @Test fun fan_on() = assertEquals(VoiceIntent.Fan(Command.PowerOn), p("fan on"))
    @Test fun fan_off() = assertEquals(VoiceIntent.Fan(Command.PowerOff), p("turn the fan off"))
    @Test fun fan_speed3() = assertEquals(VoiceIntent.Fan(Command.FanSpeed(3)), p("fan speed 3"))
    @Test fun fan_speedWord() = assertEquals(VoiceIntent.Fan(Command.FanSpeed(5)), p("set fan to speed five"))
    @Test fun fan_boost() = assertEquals(VoiceIntent.Fan(Command.Boost), p("fan boost"))
    @Test fun fan_light() = assertEquals(VoiceIntent.Fan(Command.Light), p("fan light"))
    @Test fun fan_timer() = assertEquals(VoiceIntent.Fan(Command.Timer), p("fan timer"))
    @Test fun fan_toggle() = assertEquals(VoiceIntent.Fan(Command.Power), p("fan"))
    @Test fun fan_hinglishBand() = assertEquals(VoiceIntent.Fan(Command.PowerOff), p("pankha band karo"))
    @Test fun fan_hinglishSpeed() = assertEquals(VoiceIntent.Fan(Command.FanSpeed(2)), p("pankha do pe karo"))
    @Test fun fan_devanagari() = assertEquals(VoiceIntent.Fan(Command.PowerOn), p("पंखा चालू करो"))
    @Test fun fan_marathiSpeed() = assertEquals(VoiceIntent.Fan(Command.FanSpeed(4)), p("पंखा चार"))
    @Test fun fan_speedOutOfRangeIsToggle() = assertEquals(VoiceIntent.Fan(Command.Power), p("fan 9"))

    // ---- Hindi / Hinglish ----
    @Test fun hi_volumeBadhao() = assertEquals(VoiceIntent.Send(Command.VolUp, Target.TV), p("volume badhao"))
    @Test fun hi_awaazKam() = assertEquals(VoiceIntent.Send(Command.VolDown, Target.TV), p("awaaz kam karo"))
    @Test fun hi_awaazBand() = assertEquals(VoiceIntent.Send(Command.Mute, Target.TV), p("awaaz band"))
    @Test fun hi_channelEkSauBees() = assertEquals(VoiceIntent.Channel(120), p("channel ek sau bees"))
    @Test fun hi_channelPachees() = assertEquals(VoiceIntent.Channel(25), p("channel pachees lagao"))
    @Test fun hi_lagao() = assertEquals(VoiceIntent.Channel(120), p("120 lagao"))
    @Test fun hi_bandKar() = assertEquals(VoiceIntent.Send(Command.PowerOff, Target.TV), p("band kar"))
    @Test fun hi_tvChaluKaro() = assertEquals(VoiceIntent.Send(Command.PowerOn, Target.TV), p("tv chalu karo"))
    @Test fun hi_boxBandKaro() = assertEquals(VoiceIntent.Send(Command.PowerOff, Target.BOX), p("box band karo"))
    @Test fun hi_aglaChannel() = assertEquals(VoiceIntent.Send(Command.ChUp, Target.BOX), p("agla channel"))
    @Test fun hi_sabBand() = assertEquals(VoiceIntent.RunMacro("All off"), p("sab band karo"))
    @Test fun hi_youtubeKholo() = assertEquals(VoiceIntent.OpenApp("youtube"), p("youtube kholo"))
    @Test fun hi_devanagariChannel() = assertEquals(VoiceIntent.Channel(5), p("चैनल पांच"))
    @Test fun hi_devanagariVolume() = assertEquals(VoiceIntent.Send(Command.VolUp, Target.TV), p("आवाज़ बढ़ाओ"))
    @Test fun hi_devanagariMute() = assertEquals(VoiceIntent.Send(Command.Mute, Target.TV), p("आवाज़ बंद करो"))
    @Test fun hi_devanagariTvOff() = assertEquals(VoiceIntent.Send(Command.PowerOff, Target.TV), p("टीवी बंद करो"))
    @Test fun hi_devanagariChannelDigits() = assertEquals(VoiceIntent.Channel(220), p("चैनल 220"))
    @Test fun hi_sleepMinuteMeinBand() = assertEquals(VoiceIntent.Sleep(20), p("20 minute mein band karo"))
    @Test fun hi_peeche() = assertEquals(VoiceIntent.Send(Command.Back, Target.ANY), p("peeche"))

    // ---- Marathi ----
    @Test fun mr_channelPaach() = assertEquals(VoiceIntent.Channel(5), p("चॅनल पाच"))
    @Test fun mr_channelDigits() = assertEquals(VoiceIntent.Channel(12), p("चॅनल 12 लाव"))
    @Test fun mr_awaajVadhav() = assertEquals(VoiceIntent.Send(Command.VolUp, Target.TV), p("आवाज वाढव"))
    @Test fun mr_awaajKami() = assertEquals(VoiceIntent.Send(Command.VolDown, Target.TV), p("आवाज कमी कर"))
    @Test fun mr_tvBand() = assertEquals(VoiceIntent.Send(Command.PowerOff, Target.TV), p("टीव्ही बंद कर"))
    @Test fun mr_tvSuru() = assertEquals(VoiceIntent.Send(Command.PowerOn, Target.TV), p("टीव्ही सुरू कर"))
    @Test fun mr_pudhchaChannel() = assertEquals(VoiceIntent.Send(Command.ChUp, Target.BOX), p("पुढचा चॅनल"))
    @Test fun mr_sagleBand() = assertEquals(VoiceIntent.RunMacro("All off"), p("सगळं बंद कर"))
    @Test fun mr_thamb() = assertEquals(VoiceIntent.Send(Command.Pause, Target.ANY), p("थांब"))
}
