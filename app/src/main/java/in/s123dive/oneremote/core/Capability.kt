package `in`.s123dive.oneremote.core

/**
 * What a device (or one of its transports) can do. A command is only sent when the
 * transport declares the command's capability; otherwise the UI shows an explicit
 * "not supported" rather than a silent no-op.
 */
enum class Capability {
    /** Power toggle / on / off. */
    POWER,
    /** VolUp, VolDown, Mute. */
    VOLUME,
    /** ChUp, ChDown, Channel(n). */
    CHANNEL,
    /** Digit(n). */
    DIGITS,
    /** Up/Down/Left/Right/Ok. */
    NAVIGATION,
    /** Back, Home, Menu, Guide, Info, Exit. */
    MENU,
    /** Play/Pause/Stop/Rew/Fwd. */
    PLAYBACK,
    /** Red/Green/Yellow/Blue. */
    COLOUR_KEYS,
    /** Input cycle and InputSelect(id). */
    INPUT,
    /** Text(s) injection. */
    TEXT_INPUT,
    /** PointerMove / PointerClick / Scroll. */
    POINTER,
    /** LaunchApp(id). */
    APP_LAUNCH,
    /** Device can be woken from standby over the network. */
    WAKE_ON_LAN,
    /** Device accepts an absolute volume level. */
    VOLUME_ABSOLUTE,
    /** Raw(bytes) expert payloads. */
    RAW,
    /** FanSpeed(n), Boost, Timer, Light. */
    FAN,
}

/** The capability a transport must declare to carry this command. */
val Command.capability: Capability
    get() = when (this) {
        Command.Power, Command.PowerOn, Command.PowerOff -> Capability.POWER
        Command.VolUp, Command.VolDown, Command.Mute -> Capability.VOLUME
        Command.ChUp, Command.ChDown, is Command.Channel -> Capability.CHANNEL
        is Command.Digit -> Capability.DIGITS
        Command.Ok, Command.Up, Command.Down, Command.Left, Command.Right -> Capability.NAVIGATION
        Command.Back, Command.Home, Command.Menu, Command.Guide, Command.Info, Command.Exit,
        Command.ChannelList, Command.Fav, Command.Prev, Command.Subtitle, Command.Audio, Command.Sleep, Command.Help -> Capability.MENU
        Command.Play, Command.Pause, Command.Stop, Command.Rew, Command.Fwd, Command.Record -> Capability.PLAYBACK
        Command.Red, Command.Green, Command.Yellow, Command.Blue -> Capability.COLOUR_KEYS
        Command.Input, is Command.InputSelect -> Capability.INPUT
        is Command.LaunchApp -> Capability.APP_LAUNCH
        is Command.PointerMove, Command.PointerClick, is Command.Scroll -> Capability.POINTER
        is Command.Text -> Capability.TEXT_INPUT
        is Command.Raw -> Capability.RAW
        is Command.FanSpeed, Command.Boost, Command.Timer, Command.Light -> Capability.FAN
    }
