package `in`.s123dive.oneremote.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.automation.Macros
import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.Rooms
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Source
import `in`.s123dive.oneremote.core.capability
import `in`.s123dive.oneremote.lg.LgTransport
import `in`.s123dive.oneremote.sony.SonyTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phone-side speech → [IntentParser] → Router. On-device recognition when the phone offers it.
 * Every outcome is displayed: transcript, parsed action, and the result or why it is unsupported.
 */
class VoiceController(private val context: Context, private val graph: AppGraph) {
    data class State(
        val available: Boolean,
        val onDevice: Boolean,
        val listening: Boolean = false,
        val partial: String = "",
        val transcript: String? = null,
        val action: String? = null,
        val result: String? = null,
        val error: String? = null,
    )

    private val available = SpeechRecognizer.isRecognitionAvailable(context)
    private val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private val _state = MutableStateFlow(State(available, onDevice))
    val state: StateFlow<State> = _state
    private var recognizer: SpeechRecognizer? = null

    fun start(language: String?) {
        if (!available) { _state.update { it.copy(error = "Speech recognition is not available on this phone") }; return }
        stop()
        val r = if (Build.VERSION.SDK_INT >= 31 && onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.update { it.copy(listening = true, partial = "", transcript = null, action = null, result = null, error = null) } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { _state.update { it.copy(listening = false) } }
            override fun onError(error: Int) {
                _state.update { it.copy(listening = false, error = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Recogniser needs network (no on-device model installed)"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, 12 -> "Language not supported by the recogniser"
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, 13 -> "Language pack not downloaded"
                    else -> "Recogniser error $error"
                }) }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                _state.update { it.copy(listening = false, transcript = text) }
                handle(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                _state.update { it.copy(partial = text) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        }
        r.startListening(intent)
    }

    fun stop() { recognizer?.stopListening() }
    fun destroy() { recognizer?.destroy(); recognizer = null }

    /** Parses and dispatches typed or recognised text (also used by tests via [IntentParser]). */
    fun handle(text: String) {
        val intent = IntentParser.parse(text)
        _state.update { it.copy(transcript = text, action = describe(intent), result = null) }
        val state = graph.repository.current()
        val room = state.room(graph.lastRoomId() ?: "") ?: state.rooms.firstOrNull()
        if (room == null) { _state.update { it.copy(result = "No room set up yet") }; return }
        graph.appScope.launch { val r = dispatch(intent, room); _state.update { it.copy(result = r) } }
    }

    private fun describe(i: VoiceIntent): String = when (i) {
        is VoiceIntent.Send -> "${i.cmd.key}${if (i.target != VoiceIntent.Target.ANY) " → ${i.target}" else ""}"
        is VoiceIntent.Channel -> "Channel ${i.n}"
        is VoiceIntent.SwitchInput -> "Switch TV to HDMI ${i.hdmiPort}"
        is VoiceIntent.OpenApp -> "Open app \"${i.name}\""
        is VoiceIntent.TypeText -> if (i.enter) "Search \"${i.text}\"" else "Type \"${i.text}\""
        is VoiceIntent.Sleep -> "TV off in ${i.minutes} min"
        is VoiceIntent.RunMacro -> "Macro ${i.name}"
        is VoiceIntent.Unparsed -> "Didn't get that: \"${i.text}\""
    }

    private fun zoneFor(room: Room, cmd: Command, target: VoiceIntent.Target): Role {
        val boxCaps = setOf(Capability.CHANNEL, Capability.DIGITS, Capability.NAVIGATION, Capability.MENU, Capability.COLOUR_KEYS, Capability.PLAYBACK)
        val zone = when (target) {
            VoiceIntent.Target.TV -> Role.TV
            VoiceIntent.Target.BOX -> if (room.stbId != null) Role.STB else Role.TV
            VoiceIntent.Target.ANY -> if (room.stbId != null && cmd.capability in boxCaps) Role.STB else Role.TV
        }
        return Rooms.targetRole(room, zone, cmd)
    }

    private suspend fun send(room: Room, cmd: Command, target: VoiceIntent.Target): String {
        val role = zoneFor(room, cmd, target)
        return when (val r = graph.router.send(RouteRequest(room.id, role, cmd, Source.VOICE))) {
            is RouteResult.Sent -> "Sent to ${role.name} via ${r.transport.badge}"
            is RouteResult.Failed -> "Failed: ${r.reason}"
            is RouteResult.Unsupported -> r.reason
        }
    }

    private suspend fun dispatch(i: VoiceIntent, room: Room): String {
        val state = graph.repository.current()
        val tv = room.tvId?.let { state.device(it) }
        return when (i) {
            is VoiceIntent.Send -> send(room, i.cmd, i.target)
            is VoiceIntent.Channel -> send(room, Command.Channel(i.n), VoiceIntent.Target.BOX)
            is VoiceIntent.RunMacro -> {
                val m = Macros.find(state, room.id, i.name) ?: return "No macro named ${i.name} in ${room.name}"
                val results = graph.macros.run(m, Source.VOICE)
                "${m.name}: ${results.count { it is RouteResult.Sent }}/${results.size} steps sent"
            }
            is VoiceIntent.Sleep -> {
                if (tv == null) return "This room has no TV"
                val note = graph.sleepTimer.schedule(room.id, i.minutes)
                graph.settings.setSleepTimer(room.id, System.currentTimeMillis() + i.minutes * 60_000L)
                "TV off in ${i.minutes} min" + (note?.let { " ($it)" } ?: "")
            }
            is VoiceIntent.SwitchInput -> {
                if (tv == null) return "This room has no TV"
                val id = when (tv.type) {
                    DeviceType.LG_WEBOS -> "HDMI_${i.hdmiPort}"
                    DeviceType.SONY_BRAVIA -> "extInput:hdmi?port=${i.hdmiPort}"
                    DeviceType.AIRTEL_HD, DeviceType.ATOMBERG_FAN -> return "Not supported on this device"
                }
                send(room, Command.InputSelect(id), VoiceIntent.Target.TV)
            }
            is VoiceIntent.OpenApp -> {
                if (tv == null) return "This room has no TV"
                if (tv.type == DeviceType.AIRTEL_HD) return "Not supported on this box"
                val device = graph.registry.get(tv) ?: return "${tv.name} has no driver"
                val apps = device.transports.filterIsInstance<LgTransport>().firstOrNull()?.apps()?.map { it.id to it.title }
                    ?: device.transports.filterIsInstance<SonyTransport>().firstOrNull()?.apps()?.map { it.uri to it.title }
                    ?: return "App list needs the Wi-Fi connection to ${tv.name}"
                val wanted = i.name.lowercase()
                val match = apps.firstOrNull { it.second.lowercase() == wanted }
                    ?: apps.firstOrNull { it.second.lowercase().contains(wanted) || wanted.contains(it.second.lowercase()) }
                    ?: return "No app called \"${i.name}\" on ${tv.name} (${apps.size} apps listed)"
                send(room, Command.LaunchApp(match.first), VoiceIntent.Target.TV).let { "${match.second}: $it" }
            }
            is VoiceIntent.TypeText -> {
                if (tv == null) return "This room has no TV"
                if (tv.type == DeviceType.AIRTEL_HD) return "Not supported on this box"
                send(room, Command.Text(i.text + if (i.enter) "\n" else ""), VoiceIntent.Target.TV)
            }
            is VoiceIntent.Unparsed -> "Didn't get that: \"${i.text}\""
        }
    }
}
