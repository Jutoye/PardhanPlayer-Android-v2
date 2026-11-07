package com.pardhancodes.pardhanplayer

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("pardhan_prefs", Context.MODE_PRIVATE)

    // ---- watch history / resume ----
    fun savePosition(uri: String, title: String, posMs: Long) {
        if (uri.isBlank()) return
        sp.edit().putLong("pos_$uri", posMs)
            .putString("last_uri", uri).putString("last_title", title).putLong("last_pos", posMs).apply()
    }
    fun position(uri: String): Long = sp.getLong("pos_$uri", 0L)
    fun lastUri(): String? = sp.getString("last_uri", null)
    fun lastTitle(): String? = sp.getString("last_title", null)
    fun lastPos(): Long = sp.getLong("last_pos", 0L)
    fun clearHistory() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("pos_") || it.startsWith("last_") }.forEach { e.remove(it) }
        e.apply()
    }

    // ---- vault PIN ----
    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("pardhan-vault:$pin").toByteArray())
            .joinToString("") { "%02x".format(it) }
    fun hasPin() = sp.contains("pin_hash")
    fun setPin(pin: String) { sp.edit().putString("pin_hash", hash(pin)).apply() }
    fun checkPin(pin: String) = sp.getString("pin_hash", null) == hash(pin)

    // ---- audio settings ----
    var preampPct: Int
        get() = sp.getInt("preamp", 100)
        set(v) { sp.edit().putInt("preamp", v).apply() }
    var bassStrength: Int
        get() = sp.getInt("bass", 0)
        set(v) { sp.edit().putInt("bass", v).apply() }
    fun eqLevel(band: Int): Int? = if (sp.contains("eq_$band")) sp.getInt("eq_$band", 0) else null
    fun setEqLevel(band: Int, mb: Int) { sp.edit().putInt("eq_$band", mb).apply() }

    // ---- backup / restore (settings + watch history, never the PIN) ----
    fun exportJson(): String {
        val o = JSONObject()
        sp.all.forEach { (k, v) -> if (k != "pin_hash" && v != null) o.put(k, v) }
        return o.toString(2)
    }
    fun importJson(text: String) {
        val o = JSONObject(text)
        val e = sp.edit()
        o.keys().forEach { k ->
            if (k == "pin_hash") return@forEach
            when (val v = o.get(k)) {
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is String -> e.putString(k, v)
                is Boolean -> e.putBoolean(k, v)
            }
        }
        e.apply()
    }
}
