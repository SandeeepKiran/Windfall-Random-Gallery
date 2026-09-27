package com.mousy.windfall.multivideo

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Multi-Video on its own: which videos go where, the wall's settings, and the players. Kept
 * apart from the gallery's ViewModel, which knows nothing about it.
 *
 * Nothing here is saved across app restarts. Videos chosen through Android's picker or another
 * app can only be read while the app runs, so a saved wall would come back broken.
 */
class MultiVideoViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MultiVideoState())
    val state: StateFlow<MultiVideoState> = _state.asStateFlow()

    /** The open video picker and what it is choosing for; null when no picker is open. */
    private val _picker = MutableStateFlow<PickerTarget?>(null)
    val picker: StateFlow<PickerTarget?> = _picker.asStateFlow()

    private val wallPlayers = WallPlayers(application)

    /** One player per slot; null where a slot has no video on the wall right now. */
    val players: StateFlow<List<Player?>> = wallPlayers.players

    /** True while the wall is on screen with the app in front. */
    private val wallShowing = MutableStateFlow(false)

    init {
        // viewModelScope runs on the main thread, which ExoPlayer requires.
        viewModelScope.launch {
            combine(_state, wallShowing) { state, showing -> state to (showing && state.wallOpen) }
                .collect { (state, active) -> wallPlayers.sync(state, active) }
        }
    }

    fun setLayout(layout: WallLayout) = _state.update { it.copy(layout = layout) }

    fun setRotation(rotation: WallRotation) = _state.update { it.copy(rotation = rotation) }

    fun cycleRotation() = _state.update { it.copy(rotation = it.rotation.next()) }

    fun toggleFill() = _state.update { it.copy(fill = !it.fill) }

    fun openWall() {
        if (!_state.value.hasVideos) return
        wallPlayers.forgetPauses()
        _state.update { it.copy(wallOpen = true) }
    }

    fun closeWall() {
        _picker.value = null
        _state.update { it.copy(wallOpen = false) }
    }

    /** Reported by the wall screen: players run only while it is visible. */
    fun onWallShowing(showing: Boolean) {
        wallShowing.value = showing
    }

    fun openPicker(target: PickerTarget) {
        _picker.value = target
    }

    fun closePicker() {
        _picker.value = null
    }

    /** Videos chosen from Windfall's own list; their names are already known. */
    fun choose(videos: List<WallVideo>) {
        val target = takePickerTarget()
        if (videos.isEmpty()) return
        _state.update { it.withVideos(target, videos.take(target.maxCount)) }
    }

    /** Videos handed back by Android's photo picker or another app. Names are looked up first. */
    fun chooseUris(uris: List<Uri>) {
        val target = takePickerTarget()
        val chosen = uris.distinct().take(target.maxCount)
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val videos = withContext(Dispatchers.IO) {
                chosen.map { WallVideo(uri = it.toString(), name = displayName(it)) }
            }
            _state.update { it.withVideos(target, videos) }
        }
    }

    fun clearSlot(slot: Int) = _state.update { it.withSlotCleared(slot) }

    fun swapSlots(first: Int, second: Int) = _state.update { it.withSlotsSwapped(first, second) }

    fun toggleSound(slot: Int) = _state.update { it.withSoundToggled(slot) }

    fun toggleWallSound() = _state.update { it.withWallSoundToggled() }

    fun playAll() = wallPlayers.playAll()

    fun pauseAll() = wallPlayers.pauseAll()

    fun restartAll() = wallPlayers.restartAll()

    fun togglePlay(slot: Int) = wallPlayers.togglePlay(slot)

    fun seekBy(slot: Int, deltaMs: Long) = wallPlayers.seekBy(slot, deltaMs)

    fun seekTo(slot: Int, positionMs: Long) = wallPlayers.seekTo(slot, positionMs)

    override fun onCleared() {
        wallPlayers.releaseAll()
        super.onCleared()
    }

    /**
     * Closes the picker and says what it was choosing for. A picker result can outlive the
     * target (Android may restart the app while its picker is open); the whole wall is the
     * sensible guess then.
     */
    private fun takePickerTarget(): PickerTarget {
        val target = _picker.value ?: PickerTarget.wholeWall(_state.value.layout)
        _picker.value = null
        return target
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Video"
    }
}
