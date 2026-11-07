package com.pardhancodes.pardhanplayer.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import com.pardhancodes.pardhanplayer.Prefs

/** Lives in the same process as the playback service, so the UI can reach the effects directly. */
object AudioFx {
    var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null

    fun attach(sessionId: Int, prefs: Prefs) {
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching { equalizer = Equalizer(0, sessionId).apply { enabled = true } }
        runCatching { bass = BassBoost(0, sessionId).apply { enabled = true } }
        runCatching { loud = LoudnessEnhancer(sessionId).apply { enabled = true } }
        applyPrefs(prefs)
    }

    fun applyPrefs(prefs: Prefs) {
        equalizer?.let { eq ->
            for (b in 0 until eq.numberOfBands) {
                prefs.eqLevel(b)?.let { lvl -> setBand(b, lvl) }
            }
        }
        setPreamp(prefs.preampPct)
        setBass(prefs.bassStrength)
    }

    fun setBand(band: Int, millibel: Int) {
        runCatching { equalizer?.setBandLevel(band.toShort(), millibel.toShort()) }
    }

    /** 100% = normal, 200% = about +6 dB. */
    fun setPreamp(pct: Int) {
        runCatching { loud?.setTargetGain(((pct - 100) * 6).coerceAtLeast(0)) }
    }

    fun setBass(strength: Int) {
        runCatching { bass?.setStrength(strength.toShort()) }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { loud?.release() }
        equalizer = null; bass = null; loud = null
    }
}
