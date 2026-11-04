package `in`.s123dive.oneremote.voice

import `in`.s123dive.oneremote.core.Command

/** What a spoken sentence asks for. Role / device resolution happens in [VoiceController]. */
sealed interface VoiceIntent {
    enum class Target { TV, BOX, ANY }

    data class Send(val cmd: Command, val target: Target = Target.ANY) : VoiceIntent
    data class Channel(val n: Int) : VoiceIntent
    data class SwitchInput(val hdmiPort: Int) : VoiceIntent
    data class OpenApp(val name: String) : VoiceIntent
    data class TypeText(val text: String, val enter: Boolean) : VoiceIntent
    data class Sleep(val minutes: Int) : VoiceIntent
    data class RunMacro(val name: String) : VoiceIntent
    data class Unparsed(val text: String) : VoiceIntent
}

/**
 * Pure text → intent parser for English, Hindi / Hinglish (Latin and Devanagari) and Marathi.
 * Numbers may be digits or words ("ek sau bees" = 120, "चॅनल पाच" = channel 5).
 * Unknown sentences come back as [VoiceIntent.Unparsed] so the UI can show "Didn't get that".
 */
object IntentParser {
    private val units = mapOf(
        // English
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        // Hinglish 0..99
        "shunya" to 0, "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5, "chhe" to 6, "che" to 6, "chah" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10, "dus" to 10,
        "gyarah" to 11, "barah" to 12, "terah" to 13, "chaudah" to 14, "pandrah" to 15, "solah" to 16, "satrah" to 17, "atharah" to 18, "unnis" to 19, "bees" to 20, "bis" to 20,
        "ikkis" to 21, "bais" to 22, "teis" to 23, "chaubis" to 24, "pachees" to 25, "pachis" to 25, "chhabbis" to 26, "sattais" to 27, "atthais" to 28, "untis" to 29, "tees" to 30, "tis" to 30,
        "ikattis" to 31, "battis" to 32, "taintis" to 33, "chauntis" to 34, "paintis" to 35, "chhattis" to 36, "saintis" to 37, "adtis" to 38, "untalis" to 39, "chalis" to 40, "chaalis" to 40,
        "iktalis" to 41, "bayalis" to 42, "taintalis" to 43, "chauvalis" to 44, "paintalis" to 45, "chhiyalis" to 46, "saintalis" to 47, "adtalis" to 48, "unchas" to 49, "pachas" to 50,
        "ikyavan" to 51, "bavan" to 52, "tirpan" to 53, "chauvan" to 54, "pachpan" to 55, "chhappan" to 56, "sattavan" to 57, "atthavan" to 58, "unsath" to 59, "saath" to 60,
        "iksath" to 61, "basath" to 62, "tirsath" to 63, "chaunsath" to 64, "painsath" to 65, "chhiyasath" to 66, "sadsath" to 67, "adsath" to 68, "unhattar" to 69, "sattar" to 70,
        "ikhattar" to 71, "bahattar" to 72, "tihattar" to 73, "chauhattar" to 74, "pachhattar" to 75, "chhihattar" to 76, "sathattar" to 77, "athhattar" to 78, "unasi" to 79, "assi" to 80,
        "ikyasi" to 81, "bayasi" to 82, "tirasi" to 83, "chaurasi" to 84, "pachasi" to 85, "chhiyasi" to 86, "sattasi" to 87, "atthasi" to 88, "navasi" to 89, "nabbe" to 90,
        "ikyanve" to 91, "banve" to 92, "tiranve" to 93, "chauranve" to 94, "pachanve" to 95, "chhiyanve" to 96, "sattanve" to 97, "atthanve" to 98, "ninyanve" to 99,
        // Hindi (Devanagari)
        "शून्य" to 0, "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "छः" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10,
        "ग्यारह" to 11, "बारह" to 12, "तेरह" to 13, "चौदह" to 14, "पंद्रह" to 15, "सोलह" to 16, "सत्रह" to 17, "अठारह" to 18, "उन्नीस" to 19, "बीस" to 20, "पच्चीस" to 25,
        "तीस" to 30, "चालीस" to 40, "पचास" to 50, "साठ" to 60, "सत्तर" to 70, "अस्सी" to 80, "नब्बे" to 90,
        // Marathi
        "दोन" to 2, "पाच" to 5, "सहा" to 6, "नऊ" to 9, "दहा" to 10, "अकरा" to 11, "बारा" to 12, "तेरा" to 13, "चौदा" to 14, "पंधरा" to 15, "सोळा" to 16, "सतरा" to 17, "अठरा" to 18, "एकोणीस" to 19, "वीस" to 20,
        "पंचवीस" to 25, "चाळीस" to 40, "पन्नास" to 50, "ऐंशी" to 80, "नव्वद" to 90,
    )
    private val hundreds = setOf("hundred", "sau", "सौ", "शंभर", "so")

    /** Parses a run of number tokens starting at [from]; returns value and tokens consumed, or null. */
    internal fun number(tokens: List<String>, from: Int): Pair<Int, Int>? {
        var i = from
        var total = 0
        var current = 0
        var any = false
        while (i < tokens.size) {
            val t = tokens[i]
            val digits = t.toIntOrNull()
            when {
                digits != null -> { if (any && current == 0 && total == 0) break; current += digits; any = true }
                t in units -> { current += units.getValue(t); any = true }
                t in hundreds -> { current = (if (current == 0) 1 else current) * 100; total += current; current = 0; any = true }
                t == "and" || t == "aur" -> if (!any) break
                else -> break
            }
            i++
        }
        if (!any) return null
        return (total + current) to (i - from)
    }

    private fun normalise(text: String): List<String> =
        text.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

    private val fillers = setOf("please", "the", "a", "an", "to", "kar", "karo", "kardo", "do", "dijiye", "kijiye", "करो", "कर", "करा", "कीजिए", "हो", "jao", "jaa", "haan", "zara", "thoda", "ज़रा", "थोड़ा", "कृपया")
    private fun strip(tokens: List<String>) = tokens.filter { it !in fillers }
    private fun has(tokens: List<String>, vararg words: String) = words.any { it in tokens }
    private fun phrase(text: String, vararg phrases: String) = phrases.any { text.contains(it) }

    fun parse(raw: String): VoiceIntent {
        val original = raw.trim()
        if (original.isEmpty()) return VoiceIntent.Unparsed(original)
        val tokens = normalise(original)
        val joined = tokens.joinToString(" ")
        val t = strip(tokens)
        val j = t.joinToString(" ")

        // ---- macros ----
        if (phrase(joined, "watch tv", "tv dekh", "tv dekhna", "टीवी देख", "टीव्ही बघ", "tv lagao", "start tv")) return VoiceIntent.RunMacro("Watch TV")
        if (phrase(joined, "all off", "sab band", "sab kuch band", "sab kuchh band", "everything off", "सब बंद", "सगळं बंद", "सगळे बंद", "turn everything off")) return VoiceIntent.RunMacro("All off")

        // ---- sleep timer ----
        if (has(tokens, "sleep", "timer", "सो", "झोप") || phrase(joined, "minute mein band", "minute me band", "मिनट में बंद", "मिनिटात बंद", "minutes band")) {
            tokens.indices.forEach { i -> number(tokens, i)?.let { (n, _) -> if (n in 1..600) return VoiceIntent.Sleep(n) } }
        }

        // ---- text / search / app ----
        tokens.indexOfFirst { it in setOf("search", "dhundo", "dhoondo", "khojo", "खोजो", "ढूंढो", "शोध", "सर्च") }.takeIf { it >= 0 }?.let { i ->
            val rest = tokens.drop(i + 1).filter { it !in setOf("for", "ke", "liye", "के", "लिए") }
            if (rest.isNotEmpty()) return VoiceIntent.TypeText(rest.joinToString(" "), enter = true)
        }
        tokens.indexOfFirst { it in setOf("type", "likho", "लिखो", "लिहा", "टाइप") }.takeIf { it >= 0 }?.let { i ->
            val rest = tokens.drop(i + 1)
            if (rest.isNotEmpty()) return VoiceIntent.TypeText(rest.joinToString(" "), enter = false)
        }
        tokens.indexOfFirst { it in setOf("open", "launch", "kholo", "खोलो", "उघड", "chalao", "चलाओ", "start") }.takeIf { it >= 0 }?.let { i ->
            val after = strip(tokens.drop(i + 1)).filter { it !in setOf("app", "application", "ऐप", "अ‍ॅप") }
            val rest = if (after.isNotEmpty()) after else strip(tokens.take(i)).filter { it !in setOf("app", "application", "ऐप", "अ‍ॅप") } // "youtube kholo"
            if (rest.isNotEmpty() && !has(rest, "tv", "टीवी", "टीव्ही", "box")) return VoiceIntent.OpenApp(rest.joinToString(" "))
        }

        // ---- HDMI / input ----
        if (has(tokens, "hdmi", "एचडीएमआई")) {
            val i = tokens.indexOfFirst { it == "hdmi" || it == "एचडीएमआई" }
            val n = number(tokens, i + 1)?.first ?: tokens.getOrNull(i)?.let { Regex("hdmi(\\d)").find(joined)?.groupValues?.get(1)?.toInt() }
            if (n != null) return VoiceIntent.SwitchInput(n)
        }
        Regex("hdmi\\s*(\\d)").find(joined)?.let { return VoiceIntent.SwitchInput(it.groupValues[1].toInt()) }
        if (has(t, "input", "source", "इनपुट", "सोर्स")) return VoiceIntent.Send(Command.Input, VoiceIntent.Target.TV)

        // ---- volume ----
        val volumeWord = has(tokens, "volume", "awaaz", "awaz", "aawaz", "sound", "आवाज़", "आवाज", "वॉल्यूम", "volum")
        val up = has(t, "up", "badhao", "badha", "badhaao", "increase", "louder", "zyada", "jyada", "बढ़ाओ", "बढ़ा", "बढाव", "वाढव", "वाढवा", "ज्यादा", "raise", "high")
        val down = has(t, "down", "kam", "ghatao", "ghata", "decrease", "lower", "quieter", "कम", "कमी", "घटाओ", "reduce", "low", "softer")
        if (has(t, "mute", "silent", "chup", "म्यूट", "शांत", "मूक") || (volumeWord && has(t, "band", "बंद", "off"))) return VoiceIntent.Send(Command.Mute, VoiceIntent.Target.TV)
        if (has(t, "unmute", "अनम्यूट")) return VoiceIntent.Send(Command.Mute, VoiceIntent.Target.TV)
        if (volumeWord && up) return VoiceIntent.Send(Command.VolUp, VoiceIntent.Target.TV)
        if (volumeWord && down) return VoiceIntent.Send(Command.VolDown, VoiceIntent.Target.TV)
        if (has(t, "louder", "quieter")) return VoiceIntent.Send(if (has(t, "louder")) Command.VolUp else Command.VolDown, VoiceIntent.Target.TV)

        // ---- channel ----
        val channelWord = tokens.indexOfFirst { it in setOf("channel", "chanel", "chaanal", "चैनल", "चॅनल", "चैनेल", "ch", "channal") }
        if (channelWord >= 0) {
            if (has(t, "up", "next", "agla", "aage", "अगला", "आगे", "पुढचा", "पुढे", "badlo")) return VoiceIntent.Send(Command.ChUp, VoiceIntent.Target.BOX)
            if (has(t, "down", "previous", "back", "pichla", "peeche", "पिछला", "पीछे", "मागचा", "मागे")) return VoiceIntent.Send(Command.ChDown, VoiceIntent.Target.BOX)
            val after = tokens.drop(channelWord + 1).filter { it !in setOf("number", "no", "नंबर", "नम्बर", "lagao", "लगाओ", "लाव", "pe", "par", "पर") }
            number(after, 0)?.let { return VoiceIntent.Channel(it.first) }
            number(tokens, 0)?.takeIf { channelWord > 0 }?.let { return VoiceIntent.Channel(it.first) }
        }
        if (has(tokens, "lagao", "लगाओ", "लाव", "chalao") && number(tokens, 0) != null && tokens.size <= 4) return VoiceIntent.Channel(number(tokens, 0)!!.first)
        if (phrase(joined, "next channel", "agla channel", "अगला चैनल", "पुढचा चॅनल")) return VoiceIntent.Send(Command.ChUp, VoiceIntent.Target.BOX)
        if (phrase(joined, "previous channel", "pichla channel", "पिछला चैनल", "मागचा चॅनल")) return VoiceIntent.Send(Command.ChDown, VoiceIntent.Target.BOX)
        if (phrase(joined, "go to ") ) { number(tokens, tokens.indexOf("to") + 1)?.let { return VoiceIntent.Channel(it.first) } }

        // ---- power ----
        val box = has(tokens, "box", "setup", "settop", "set", "airtel", "dth", "बॉक्स", "एयरटेल", "एअरटेल", "receiver")
        val tv = has(tokens, "tv", "television", "टीवी", "टीव्ही", "टी", "टेलीविजन", "screen")
        val on = has(t, "on", "chalu", "chaalu", "start", "chalao", "चालू", "सुरू", "jalao", "जलाओ", "लगाओ")
        val off = has(t, "off", "band", "bandh", "बंद", "shutdown", "shut", "stop", "बन्द")
        val powerWord = has(t, "power", "पावर", "switch")
        when {
            box && on && !tv -> return VoiceIntent.Send(Command.PowerOn, VoiceIntent.Target.BOX)
            box && off && !tv -> return VoiceIntent.Send(Command.PowerOff, VoiceIntent.Target.BOX)
            (tv || powerWord) && on && !off -> return VoiceIntent.Send(Command.PowerOn, VoiceIntent.Target.TV)
            (tv || powerWord) && off && !on -> return VoiceIntent.Send(Command.PowerOff, VoiceIntent.Target.TV)
            box && powerWord -> return VoiceIntent.Send(Command.Power, VoiceIntent.Target.BOX)
            (tv || powerWord) && t.size <= 2 -> return VoiceIntent.Send(Command.Power, VoiceIntent.Target.TV)
            off && t.size <= 2 -> return VoiceIntent.Send(Command.PowerOff, VoiceIntent.Target.TV)
        }

        // ---- navigation / playback ----
        val nav = mapOf(
            Command.Home to listOf("home", "होम", "ghar", "main menu"),
            Command.Back to listOf("back", "peeche", "wapas", "वापस", "पीछे", "मागे", "return"),
            Command.Ok to listOf("ok", "okay", "select", "enter", "confirm", "ओके", "चुनो", "निवडा", "theek", "ठीक"),
            Command.Exit to listOf("exit", "bahar", "बाहर", "बाहेर"),
            Command.Menu to listOf("menu", "मेनू", "settings", "options"),
            Command.Guide to listOf("guide", "epg", "गाइड", "program guide"),
            Command.Info to listOf("info", "information", "जानकारी", "माहिती"),
            Command.Up to listOf("up", "upar", "ऊपर", "वर"),
            Command.Down to listOf("down", "neeche", "niche", "नीचे", "खाली"),
            Command.Left to listOf("left", "baen", "बाएं", "डावीकडे"),
            Command.Right to listOf("right", "daen", "दाएं", "उजवीकडे"),
            Command.Play to listOf("play", "resume", "chalao", "प्ले", "चालू"),
            Command.Pause to listOf("pause", "ruko", "रुको", "थांब", "पॉज"),
            Command.Stop to listOf("stop", "रोको", "थांबव"),
            Command.Fwd to listOf("forward", "fast forward", "aage", "आगे", "पुढे"),
            Command.Rew to listOf("rewind", "peeche karo", "रिवाइंड"),
        )
        for ((cmd, words) in nav) if (words.any { w -> if (w.contains(' ')) j.contains(w) else w in t }) return VoiceIntent.Send(cmd, VoiceIntent.Target.ANY)
        if (has(t, "pause")) return VoiceIntent.Send(Command.Pause)

        // bare number -> channel
        number(tokens, 0)?.let { (n, used) -> if (used == tokens.size) return VoiceIntent.Channel(n) }

        return VoiceIntent.Unparsed(original)
    }
}
