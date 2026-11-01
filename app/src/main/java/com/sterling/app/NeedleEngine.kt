package com.sterling.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer

/** Runs Cactus Needle 3 on the phone and turns what you said into tool calls. */
object NeedleEngine {
    private var weights: ByteBuffer? = null      // the engine reads the weights in place, so this must stay alive
    @Volatile var ready = false
    @Volatile var status = "not started"

    external fun nLoad(buf: ByteBuffer, len: Long): Int
    external fun nInit(system: String, tools: String): Int
    external fun nComplete(text: String, maxNew: Int): String?
    external fun nReset()
    external fun nError(): String

    private const val SYSTEM = "You are Sterling, a voice assistant on an Android phone. Pick the right tool for what the user says."

    private fun tool(n: String, d: String, vararg p: String): JSONObject {   // p = "arg:type:description"
        val props = JSONObject(); val req = JSONArray()
        for (x in p) {
            val opt = x.startsWith("?")
            val (k, ty, desc) = x.removePrefix("?").split(":", limit = 3)
            props.put(k, JSONObject().put("type", ty).put("description", desc)); if (!opt) req.put(k)
        }
        return JSONObject().put("type", "function").put("function", JSONObject().put("name", n).put("description", d)
            .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", req)))
    }

    private val TOOLS: List<JSONObject> = listOf(
        tool("call_contact", "Call a person from contacts", "name:string:Person name"),
        tool("dial", "Dial a phone number", "number:string:Digits"),
        tool("web_search", "Search the web or look something up", "query:string:Search text"),
        tool("open_app", "Open an installed app", "app:string:App name"),
        tool("toggle", "Turn a phone feature on or off", "setting:string:bluetooth, wifi, hotspot, airplane, nfc, location or data", "state:string:on or off"),
        tool("press", "Press a phone button", "what:string:home, back, recents, notifications, quick_settings, lock, screenshot or power"),
        tool("tap", "Tap text on the screen", "text:string:Text on screen"),
        tool("open_files", "Open the file manager"),
        tool("open_folder", "Open a folder such as downloads or music", "name:string:Folder name"),
        tool("open_file", "Open a file or document by name", "name:string:File name"),
        tool("play_music", "Play a song or audio file", "name:string:Song name"),
        tool("play_video", "Play a video file", "name:string:Video name"),
        tool("open_music", "Open the music app"),
        tool("send_file", "Send a file to a person", "name:string:File name", "?to:string:Contact name", "?via:string:quick share, bluetooth, whatsapp, telegram, email or message"),
        tool("receive_files", "Get ready to receive files over Bluetooth"),
        tool("settings", "Open a settings page", "page:string:bluetooth, hotspot, wifi, data, airplane, nfc, location, display, sound, battery, storage, apps, vpn or main"),
        tool("bluetooth_on", "Turn Bluetooth on"),
        tool("flashlight", "Turn the flashlight on or off", "state:string:on or off"),
        tool("media", "Control music playback", "key:string:playpause, next or prev"),
        tool("volume", "Turn the volume up, down or mute", "direction:string:up, down or mute"),
        tool("set_volume", "Set the volume to a percentage", "level:integer:Percent 0 to 100"),
        tool("brightness", "Make the screen brighter or darker", "direction:string:up or down"),
        tool("set_brightness", "Set screen brightness to a percentage", "level:integer:Percent 0 to 100"),
        tool("rotation", "Turn auto rotate on or off", "state:string:on or off"),
        tool("battery", "Tell the battery percentage"),
        tool("get_time", "Tell the time and date"),
        tool("activity_report", "Tell what the user has been doing on the phone"),
        tool("camera", "Open the camera"),
        tool("set_timer", "Start a timer", "seconds:integer:Length in seconds"),
        tool("set_alarm", "Set an alarm", "hour:integer:Hour 0 to 23", "minute:integer:Minute 0 to 59"),
        tool("navigate", "Navigate to a place", "place:string:Destination"),
        tool("scroll", "Scroll the screen", "direction:string:up or down"),
        tool("type", "Type text into the focused box", "text:string:Text to type"),
        tool("voice", "Change Sterling's own speaking voice", "action:string:next, male, female, deeper, higher, faster, slower or settings"),
        tool("sleep", "Stop listening")
    )
    private val KNOWN = TOOLS.map { it.getJSONObject("function").getString("name") }.toSet()

    /** Loads the model and tools. Safe to call from a background thread. Returns false if Needle can't run. */
    fun start(ctx: Context): Boolean {
        if (ready) return true
        try {
            System.loadLibrary("sterling_needle")
            val len = ctx.assets.openFd("needle3.cact").use { it.length }
            val buf = ByteBuffer.allocateDirect(len.toInt())
            ctx.assets.open("needle3.cact").use { s ->
                val tmp = ByteArray(1 shl 20); var n: Int
                while (s.read(tmp).also { n = it } > 0) buf.put(tmp, 0, n)
            }
            buf.rewind(); weights = buf
            val l = nLoad(buf, len)
            if (l < 0) { status = "load failed ($l): ${nError()}"; return false }
            var i = -1
            for (n in listOf(TOOLS.size, 24, 16, 10)) {   // if the tool list is too long for Needle's context, try fewer
                i = nInit(SYSTEM, JSONArray(TOOLS.take(n)).toString())
                if (i >= 0) break
            }
            if (i < 0) { status = "init failed ($i): ${nError()}"; return false }
            ready = true; status = "ready"; return true
        } catch (e: Throwable) { status = "unavailable: ${e.javaClass.simpleName}"; return false }
    }

    /** Null = Needle isn't available. Empty list = Needle ran and found no tool for this. */
    fun route(text: String): List<Call>? {
        if (!ready) return null
        val raw = synchronized(this) { nReset(); nComplete(text, 160) } ?: return null
        return try { parse(raw) } catch (e: Exception) { null }
    }

    private fun str(v: Any?): String = if (v is Number && v.toDouble() == Math.floor(v.toDouble())) v.toLong().toString() else v.toString()

    private fun parse(raw: String): List<Call> {
        val o = JSONObject(raw.substring(raw.indexOf('{')))
        val arr = o.optJSONArray("function_calls") ?: return emptyList()
        val out = mutableListOf<Call>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val name = f.optString("name")
            if (name !in KNOWN) continue
            val a = when (val r = f.opt("arguments") ?: f.opt("args") ?: f.opt("parameters")) {
                is JSONObject -> r
                is String -> try { JSONObject(r) } catch (e: Exception) { JSONObject() }
                else -> JSONObject()
            }
            val m = HashMap<String, String>()
            for (k in a.keys()) m[k] = str(a.opt(k))
            out += Call(name, m)
        }
        return out
    }
}
