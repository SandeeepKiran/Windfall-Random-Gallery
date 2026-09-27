package com.mousy.windfall.multivideo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mousy.windfall.util.PlayerWarmPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One video player per wall tile. Main thread only (an ExoPlayer requirement).
 *
 * Players exist only while the wall is on screen with the app in front: [sync] builds and
 * releases them to match. Going to the background therefore hands every video decoder back to
 * the phone, and each video carries on from where it stopped when the wall comes back.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class WallPlayers(context: Context) {

    private val appContext = context.applicationContext

    private val _players =
        MutableStateFlow<List<ExoPlayer?>>(List(MultiVideoState.MAX_SLOTS) { null })
    val players: StateFlow<List<Player?>> = _players.asStateFlow()

    /** The video each slot's player has loaded; null exactly when that slot has no player. */
    private val loaded = arrayOfNulls<String>(MultiVideoState.MAX_SLOTS)

    private data class Resume(val positionMs: Long, val playWhenReady: Boolean)

    /** Where each video was when its player went away, by address. Session only, like the viewer. */
    private val resume = object : LinkedHashMap<String, Resume>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Resume>?) =
            size > MAX_REMEMBERED
    }

    private var lastState = MultiVideoState()
    private var lastActive = false

    /** Slots paused because a call or another app briefly took the sound; resumed after. */
    private val pausedForInterruption = mutableSetOf<Int>()

    private val audioFocus = WallAudioFocus(
        appContext,
        onLost = ::pauseForFocusLoss,
        onRegained = ::resumeAfterInterruption,
    )

    /**
     * Makes the players match [state]: a player for every video on the wall while [active],
     * none otherwise. Cheap when nothing changed, so it runs on every state change.
     */
    fun sync(state: MultiVideoState, active: Boolean) {
        lastState = state
        lastActive = active
        val current = _players.value
        val next = current.toMutableList()
        var changed = false
        for (slot in 0 until MultiVideoState.MAX_SLOTS) {
            val wanted = if (active && slot < state.layout.count) state.slots[slot]?.uri else null
            val volume = if (state.audible(slot)) 1f else 0f
            if (wanted == loaded[slot]) {
                current[slot]?.volume = volume
                continue
            }
            current[slot]?.let { release(it, loaded[slot]) }
            next[slot] = wanted?.let { create(it, volume) }
            loaded[slot] = wanted
            changed = true
        }
        if (changed) _players.value = next
        updateAudioFocus()
    }

    /** Opening the wall plays everything, even videos that were paused when it was closed. */
    fun forgetPauses() {
        resume.replaceAll { _, saved -> saved.copy(playWhenReady = true) }
    }

    fun playAll() {
        eachPlayer { it.playRetryingErrors() }
        updateAudioFocus()
    }

    fun pauseAll() {
        eachPlayer { it.pause() }
        updateAudioFocus()
    }

    fun restartAll() {
        eachPlayer {
            it.seekTo(0L)
            it.playRetryingErrors()
        }
        updateAudioFocus()
    }

    fun togglePlay(slot: Int) {
        val player = _players.value.getOrNull(slot) ?: return
        if (player.playWhenReady) player.pause() else player.playRetryingErrors()
        updateAudioFocus()
    }

    fun seekBy(slot: Int, deltaMs: Long) {
        val player = _players.value.getOrNull(slot) ?: return
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = player.duration
        player.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
    }

    fun seekTo(slot: Int, positionMs: Long) {
        _players.value.getOrNull(slot)?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun releaseAll() {
        sync(lastState, active = false)
        audioFocus.abandon()
    }

    private fun create(uri: String, volume: Float): ExoPlayer {
        val saved = resume[uri]
        return PlayerWarmPool.newPlayer(appContext, decoderFallback = true).apply {
            // Unplugging headphones pauses instead of moving the sound to the loudspeaker.
            setHandleAudioBecomingNoisy(true)
            // A wall keeps going: each video starts again when it ends.
            repeatMode = Player.REPEAT_MODE_ONE
            this.volume = volume
            setMediaItem(MediaItem.fromUri(uri), saved?.positionMs ?: 0L)
            playWhenReady = saved?.playWhenReady ?: true
            prepare()
        }
    }

    private fun release(player: ExoPlayer, uri: String?) {
        if (uri != null) {
            resume[uri] = Resume(player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
        }
        player.release()
    }

    private inline fun eachPlayer(action: (ExoPlayer) -> Unit) {
        _players.value.forEach { player -> player?.let(action) }
    }

    /** A video that failed to play (a decoder was busy, say) gets another try. */
    private fun ExoPlayer.playRetryingErrors() {
        if (playerError != null) prepare()
        play()
    }

    /**
     * Holds the sound focus only while something audible is playing. A wall of muted videos
     * leaves the phone's music app alone.
     */
    private fun updateAudioFocus() {
        val wantsSound = lastActive && lastState.anyAudible &&
            _players.value.any { it?.playWhenReady == true }
        if (wantsSound) audioFocus.request() else audioFocus.abandon()
    }

    private fun pauseForFocusLoss(briefly: Boolean) {
        // Lost for good: nothing resumes by itself later, not even an earlier brief pause.
        if (!briefly) pausedForInterruption.clear()
        _players.value.forEachIndexed { slot, player ->
            if (player != null && player.playWhenReady) {
                player.pause()
                if (briefly) pausedForInterruption += slot
            }
        }
    }

    private fun resumeAfterInterruption() {
        pausedForInterruption.forEach { slot -> _players.value.getOrNull(slot)?.play() }
        pausedForInterruption.clear()
    }

    private companion object {
        /** More than any wall holds; older entries drop off. */
        const val MAX_REMEMBERED = 32
    }
}

/**
 * The wall's claim on the phone's sound. The players don't manage focus themselves because
 * several players each asking for it would pause one another.
 */
private class WallAudioFocus(
    context: Context,
    private val onLost: (briefly: Boolean) -> Unit,
    private val onRegained: () -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var held = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
        .build()

    fun request() {
        if (held || audioManager == null) return
        held = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (!held || audioManager == null) return
        audioManager.abandonAudioFocusRequest(request)
        held = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            // Another app took the sound for good: stop, and wait for the user.
            AudioManager.AUDIOFOCUS_LOSS -> {
                abandon()
                onLost(false)
            }
            // A call, an alarm, a voice prompt: pause now, carry on after.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLost(true)
            AudioManager.AUDIOFOCUS_GAIN -> onRegained()
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Android lowers the volume by itself.
        }
    }
}
