package com.mousy.windfall.multivideo

/**
 * How many videos the wall shows at once.
 *
 * Why nothing above six: every playing video holds one of the phone's hardware video decoders,
 * and most phones run out somewhere past six HD streams at once. Nine tiles would also be too
 * small to watch on a phone screen.
 */
enum class WallLayout(val count: Int) {
    ONE(1), TWO(2), THREE(3), FOUR(4), SIX(6),
}

/**
 * Which way the wall faces. LANDSCAPE and PORTRAIT stay put however the phone is turned; AUTO
 * follows how the phone is held, even while the phone's own rotation lock is on.
 */
enum class WallRotation(val label: String) {
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
    AUTO("Auto");

    fun next(): WallRotation = entries[(ordinal + 1) % entries.size]
}

/** One chosen video. The address is kept as text so the state stays a plain value. */
data class WallVideo(
    val uri: String,
    val name: String,
    val muted: Boolean = true,
)

/**
 * Which slots a picker is choosing for: the n-th chosen video goes into the n-th slot listed.
 * [clearOthers] empties every other slot too, for "choose the whole wall again".
 */
data class PickerTarget(val slots: List<Int>, val clearOthers: Boolean) {
    val maxCount: Int get() = slots.size

    companion object {
        fun single(slot: Int) = PickerTarget(listOf(slot), clearOthers = false)

        fun wholeWall(layout: WallLayout) =
            PickerTarget((0 until layout.count).toList(), clearOthers = true)
    }
}

/**
 * Everything Multi-Video remembers while the app runs: the chosen videos in slot order (the
 * top-left tile first, then row by row), how many are shown, and the wall's settings.
 * Playback itself (playing or paused, position) belongs to the players, not to this state.
 */
data class MultiVideoState(
    val layout: WallLayout = WallLayout.FOUR,
    /**
     * All [MAX_SLOTS] slots. Only the first `layout.count` are on the wall; slots past that keep
     * their video, so going from 4 to 2 and back to 4 loses nothing.
     */
    val slots: List<WallVideo?> = List(MAX_SLOTS) { null },
    val wallOpen: Boolean = false,
    /** Crop each video to fill its tile, instead of showing it whole with black bars. */
    val fill: Boolean = false,
    /** Silences the whole wall. Each video's own sound setting comes back when this is lifted. */
    val allMuted: Boolean = false,
    val rotation: WallRotation = WallRotation.LANDSCAPE,
) {
    val shownSlots: List<WallVideo?> get() = slots.take(layout.count)

    val hasVideos: Boolean get() = shownSlots.any { it != null }

    /** Whether the video in [slot] is heard right now. */
    fun audible(slot: Int): Boolean {
        if (slot >= layout.count) return false
        val video = slots.getOrNull(slot) ?: return false
        return !allMuted && !video.muted
    }

    val anyAudible: Boolean get() = (0 until layout.count).any { audible(it) }

    /**
     * Puts [videos] into the target's slots, in order. Sound: when no other shown video has
     * sound, the first new one gets it and the rest start muted, so a wall of four never starts
     * with four soundtracks at once.
     */
    fun withVideos(target: PickerTarget, videos: List<WallVideo>): MultiVideoState {
        val placed = target.slots.zip(videos).filter { (slot, _) -> slot in slots.indices }
        val placedSlots = placed.map { it.first }.toSet()
        val updated = slots.toMutableList()
        if (target.clearOthers) {
            for (i in updated.indices) if (i !in placedSlots) updated[i] = null
        }
        var soundTaken = (0 until layout.count).any { i ->
            i !in placedSlots && updated[i]?.muted == false
        }
        for ((slot, video) in placed) {
            updated[slot] = video.copy(muted = soundTaken)
            soundTaken = true
        }
        return copy(slots = updated)
    }

    fun withSlotCleared(slot: Int): MultiVideoState {
        if (slot !in slots.indices) return this
        return copy(slots = slots.toMutableList().also { it[slot] = null })
    }

    /** Swaps two tiles; either may be empty, which moves a video into an empty tile. */
    fun withSlotsSwapped(first: Int, second: Int): MultiVideoState {
        if (first !in slots.indices || second !in slots.indices || first == second) return this
        val updated = slots.toMutableList()
        updated[first] = slots[second]
        updated[second] = slots[first]
        return copy(slots = updated)
    }

    /**
     * The speaker button on one video. Turning a video's sound ON also lifts "Mute all", or the
     * tap would appear to do nothing.
     */
    fun withSoundToggled(slot: Int): MultiVideoState {
        val video = slots.getOrNull(slot) ?: return this
        val turningOn = !audible(slot)
        val updated = slots.toMutableList().also { it[slot] = video.copy(muted = !turningOn) }
        return copy(slots = updated, allMuted = if (turningOn) false else allMuted)
    }

    /**
     * The wall's own sound button: silence everything, or bring sound back. When no video was
     * set to have sound, bringing sound back gives it to the first video, so the button never
     * appears to do nothing.
     */
    fun withWallSoundToggled(): MultiVideoState {
        if (anyAudible) return copy(allMuted = true)
        val lifted = copy(allMuted = false)
        if (lifted.anyAudible) return lifted
        val first = (0 until layout.count).firstOrNull { slots[it] != null } ?: return lifted
        return lifted.withSoundToggled(first)
    }

    companion object {
        const val MAX_SLOTS = 6
    }
}

/** Tiling for the wall: no gaps, filled row by row from the top-left. */
object WallGrid {

    /**
     * Columns and rows for [count] tiles. On a wide screen short sets sit side by side; on a
     * tall screen they stack, so two landscape videos in portrait each get the full width.
     */
    fun columnsAndRows(count: Int, wide: Boolean): Pair<Int, Int> = when {
        count <= 1 -> 1 to 1
        count == 2 -> if (wide) 2 to 1 else 1 to 2
        count == 3 -> if (wide) 3 to 1 else 1 to 3
        count == 4 -> 2 to 2
        else -> if (wide) 3 to 2 else 2 to 3
    }
}

/** Video times as a player shows them: 0:07, 12:34, 1:02:03. */
object WallClock {
    fun format(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
