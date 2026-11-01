package com.sterling.app

data class Call(val name: String, val args: Map<String, String> = emptyMap())

object Brain {
    /** Needle 3 decides first. If it isn't loaded or finds nothing, the keyword rules take over. */
    fun route(text: String, useNeedle: Boolean = true): List<Call> {
        val t = clean(text)
        if (useNeedle) NeedleEngine.route(t)?.let { if (it.isNotEmpty()) return it }
        return listOfNotNull(rules(t))
    }

    fun clean(raw: String): String {
        var t = raw.lowercase().replace("'", "").replace("\u2019", "")
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val fillers = listOf("hey sterling", "ok sterling", "okay sterling", "hi sterling", "sterling", "hey", "please",
            "can you", "could you", "would you", "will you", "i want you to", "i want to", "i need you to", "go ahead and", "lets")
        var changed = true
        while (changed) {
            changed = false
            for (f in fillers) if (t == f || t.startsWith("$f ")) { t = t.removePrefix(f).trim(); changed = true }
        }
        return t.removeSuffix(" please").trim()
    }

    private fun has(t: String, vararg w: String) = w.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(t) }
    private fun m(p: String, t: String) = Regex(p).find(t)?.groupValues

    private val PAGES = listOf(
        "bluetooth" to listOf("bluetooth"), "hotspot" to listOf("hotspot", "hot spot", "tethering"),
        "wifi" to listOf("wifi", "wi fi", "wireless"), "data" to listOf("mobile data", "internet", "cellular", "data"),
        "airplane" to listOf("airplane", "aeroplane", "flight mode"), "nfc" to listOf("nfc"),
        "location" to listOf("location", "gps"), "display" to listOf("display", "screen timeout"),
        "sound" to listOf("sound settings", "do not disturb", "silent mode"), "battery" to listOf("battery saver", "power saving"),
        "storage" to listOf("storage"), "apps" to listOf("app settings"), "accessibility" to listOf("accessibility"),
        "language" to listOf("language"), "date" to listOf("date settings", "time settings"), "vpn" to listOf("vpn"),
        "main" to listOf("settings", "setting"))

    private fun rules(t: String): Call? {
        if (t.isEmpty()) return null
        val n = Regex("\\d+").find(t)?.value
        val off = has(t, "off", "disable", "stop", "close")
        val down = has(t, "down", "lower", "quieter", "decrease", "reduce", "less", "dim", "darker")

        if (has(t, "stop listening", "sleep", "go to sleep", "be quiet", "pause listening")) return Call("sleep")
        if (has(t, "voice", "talk faster", "speak faster", "talk slower", "speak slower", "sound different") &&
            !t.startsWith("open") && !has(t, "recorder", "search", "assistant"))
            return Call("voice", mapOf("action" to when {
                has(t, "female", "woman", "girl", "feminine") -> "female"
                has(t, "male", "man", "boy", "masculine") -> "male"
                has(t, "settings", "setting", "download", "more voices", "better voice") -> "settings"
                has(t, "faster", "quicker") -> "faster"
                has(t, "slower") -> "slower"
                has(t, "deeper", "lower") -> "deeper"
                has(t, "higher", "squeakier") -> "higher"
                else -> "next" }))
        if (has(t, "battery") && !has(t, "saver")) return Call("battery")
        if (has(t, "turn", "switch", "enable", "disable", "toggle")) {
            val feature = listOf("bluetooth" to listOf("bluetooth"), "wifi" to listOf("wifi", "wi fi"), "hotspot" to listOf("hotspot", "hot spot"),
                "airplane" to listOf("airplane mode", "aeroplane mode", "flight mode"), "nfc" to listOf("nfc"),
                "location" to listOf("location", "gps"), "data" to listOf("mobile data"))
            feature.firstOrNull { has(t, *it.second.toTypedArray()) }?.let {
                return Call("toggle", mapOf("setting" to it.first, "state" to if (has(t, "off", "disable")) "off" else "on"))
            }
        }
        if (has(t, "flashlight", "torch", "flash light")) return Call("flashlight", mapOf("state" to if (off) "off" else "on"))
        if (has(t, "next song", "next track", "skip", "next one")) return Call("media", mapOf("key" to "next"))
        if (has(t, "previous song", "previous track", "last song")) return Call("media", mapOf("key" to "prev"))
        if (t == "play" || has(t, "pause", "resume", "stop music", "stop the music", "stop song")) return Call("media", mapOf("key" to "playpause"))
        if (has(t, "volume", "louder", "quieter", "mute", "unmute"))
            return Call("volume", if (n != null) mapOf("level" to n) else mapOf("direction" to if (has(t, "mute")) "mute" else if (down) "down" else "up"))
        if (has(t, "brightness", "brighter", "darker", "dim"))
            return Call("brightness", if (n != null) mapOf("level" to n) else mapOf("direction" to if (down) "down" else "up"))
        if (has(t, "rotation", "rotate", "auto rotate")) return Call("rotation", mapOf("state" to if (off) "off" else "on"))
        if (has(t, "receive", "receiving", "incoming")) return Call("receive_files")
        m("^(?:send|share|transfer) (?:the |a |my )?(?:file )?(.+?)(?: to (.+?))?(?: (?:via|over|on|using|through|with|by) (.+))?$", t)?.let { g ->
            val args = mutableMapOf("name" to g[1])
            if (g[2].isNotBlank()) args["to"] = g[2]
            if (g[3].isNotBlank()) args["via"] = g[3]
            return Call("send_file", args)
        }
        m("^(?:navigate to|navigate|directions to|take me to|drive to|how do i get to) (.+)$", t)?.let { return Call("navigate", mapOf("place" to it[1])) }
        m("^(?:call|phone|dial|ring) (.+)$", t)?.let { g ->
            val digits = g[1].replace(" ", "")
            return if (digits.all { it.isDigit() } && digits.length >= 3) Call("dial", mapOf("number" to digits)) else Call("call_contact", mapOf("name" to g[1]))
        }
        if (has(t, "file explorer", "file manager", "files app", "my files") || t == "files" || t == "open files") return Call("open_files")
        m("^(?:open|show|go to|browse) (?:the |my )?(.+?) (?:folder|directory)$", t)?.let { return Call("open_folder", mapOf("name" to it[1])) }
        m("^(?:open|show|go to) (?:folder|directory) (.+)$", t)?.let { return Call("open_folder", mapOf("name" to it[1])) }
        m("^(?:open|show) (?:my )?(downloads?|movies|videos|pictures|photos|documents|dcim|camera roll|screenshots|recordings|received files)$", t)?.let { return Call("open_folder", mapOf("name" to it[1])) }
        m("^(?:open|find|show|search for|look for) (file|document|pdf|photo|picture|image|video|movie|song|music) (.+)$", t)?.let {
            return when (it[1]) { "video", "movie" -> Call("play_video", mapOf("name" to it[2])); "song", "music" -> Call("play_music", mapOf("name" to it[2]))
                else -> Call("open_file", mapOf("name" to it[2])) }
        }
        m("^(?:watch|play (?:the )?(?:video|movie|film)) (.+)$", t)?.let { return Call("play_video", mapOf("name" to it[1])) }
        m("^(?:play|listen to|stream) (.+)$", t)?.let {
            val name = it[1].replace(Regex("^(?:some |my |the |a )*(?:music|songs?|audio|track)\\s*"), "").trim()
            return if (name.isEmpty()) Call("open_music") else Call("play_music", mapOf("name" to name))
        }
        if (has(t, "camera", "selfie", "take a photo", "take a picture", "take photo")) return Call("camera")
        if (has(t, "timer") && n != null) return Call("set_timer", mapOf("seconds" to (n.toInt() * (if (has(t, "hour", "hours")) 3600 else if (has(t, "second", "seconds")) 1 else 60)).toString()))
        if (has(t, "alarm") && n != null) return Call("set_alarm", mapOf("hour" to n, "minute" to (Regex("\\d+\\D+(\\d+)").find(t)?.groupValues?.get(1) ?: "0")))
        if (has(t, "time", "date") && !has(t, "timer", "alarm")) return Call("get_time")
        if (has(t, "doing", "screen time", "usage", "used most", "what have i been")) return Call("activity_report")
        if (t == "accept" || t == "accept file" || t == "accept it") return Call("tap", mapOf("text" to "Accept"))
        m("^(?:tap|click|select|choose) (?:on )?(?:the )?(.+)$", t)?.let { return Call("tap", mapOf("text" to it[1])) }
        m("^(?:type|write|enter) (.+)$", t)?.let { return Call("type", mapOf("text" to it[1])) }
        if (has(t, "scroll down", "swipe up")) return Call("scroll", mapOf("direction" to "down"))
        if (has(t, "scroll up", "swipe down")) return Call("scroll", mapOf("direction" to "up"))
        val press = when {
            has(t, "go back") || t == "back" -> "back"
            has(t, "go home", "home screen") || t == "home" -> "home"
            has(t, "recents", "recent apps") -> "recents"
            has(t, "notifications", "notification panel") -> "notifications"
            has(t, "quick settings") -> "quick_settings"
            has(t, "lock the phone", "lock the screen", "lock screen", "lock phone") -> "lock"
            has(t, "screenshot") -> "screenshot"
            has(t, "power menu") -> "power"
            else -> null
        }
        if (press != null) return Call("press", mapOf("what" to press))
        for ((page, words) in PAGES) if (has(t, *words.toTypedArray())) {
            if (page == "bluetooth" && has(t, "on", "enable", "start")) return Call("bluetooth_on")
            return Call("settings", mapOf("page" to page))
        }
        m("^(?:search for|search the web for|search the internet for|search|google|look up|lookup|find out|bing|tell me about) (.+)$", t)?.let { return Call("web_search", mapOf("query" to it[1])) }
        if (Regex("^(who|what|whats|when|where|why|how|define|which)\\b").containsMatchIn(t) || has(t, "weather", "news", "score", "price of"))
            return Call("web_search", mapOf("query" to t))
        m("^(?:open|launch|start|run|go to|switch to|show me) (?:the |my )?(.+?)(?: app| application)?$", t)?.let { return Call("open_app", mapOf("app" to it[1])) }
        if (t.split(" ").size <= 3) return Call("open_app", mapOf("app" to t, "soft" to "1"))
        return null
    }
}
