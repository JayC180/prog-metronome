package com.jayc180.rhythmengine.persistence

import com.jayc180.rhythmengine.builder.*
import com.jayc180.rhythmengine.platform.nanoNow
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Serializes and deserializes the full project state to/from JSON (.rhy files)
 *
 * Design principles:
 *   - version field is mandatory — always checked first
 *   - format is flat and human-readable — users can edit JSON directly
 *   - soundIds are stored as-is (e.g. "snd_perc_can_hi") — missing sounds
 *     play as silence on load, everything else still works
 *   - runtime-internal fields (track IDs, cursor position) are NOT saved
 *     they are regenerated fresh on load
 *   - migration functions handle older versions cleanly
 */
object ProjectSerializer {

    const val CURRENT_VERSION = 1
    const val FILE_EXTENSION  = "rhy"

    private val json = Json {
        prettyPrint        = true    // human-readable files
        ignoreUnknownKeys  = true    // forward compatibility — newer files load in older app
        encodeDefaults     = false   // keep files compact — omit default values
    }

    // public api

    // serialize curr state -> json
    // for both manual and auto save
    fun serialize(state: TrackBuilderState, name: String): String {
        val dto = RhyProject(
            version   = CURRENT_VERSION,
            name      = name,
            bpm       = state.bpm,
            createdAt = currentIsoTimestamp(),
            tracks    = state.tracks.map { serializeTrack(it) },
        )
        return json.encodeToString(RhyProject.serializer(), dto)
    }

    // .rhy json -> TrackBuilderState
    // DeserializationException on format err
    fun deserialize(jsonString: String): TrackBuilderState {
        val raw = json.parseToJsonElement(jsonString).jsonObject
        val version = raw["version"]?.jsonPrimitive?.intOrNull
            ?: throw DeserializationException("Missing version field")

        return when (version) {
            1    -> deserializeV1(jsonString)
            else -> throw DeserializationException(
                "Unsupported version $version — update the app to open this file"
            )
        }
    }

    // for safe loading; for load from disk
    fun deserializeOrNull(jsonString: String): TrackBuilderState? =
        try { deserialize(jsonString) } catch (e: Exception) {
            null
        }

    // get basic project info
    fun peekName(jsonString: String): String? =
        try {
            json.parseToJsonElement(jsonString)
                .jsonObject["name"]?.jsonPrimitive?.content
        } catch (e: Exception) { null }

    fun peekBpm(jsonString: String): Double? =
        try {
            json.parseToJsonElement(jsonString)
                .jsonObject["bpm"]?.jsonPrimitive?.doubleOrNull
        } catch (e: Exception) { null }

    // version 1.0 serialization

    private fun serializeTrack(draft: TrackDraft): RhyTrack = RhyTrack(
        label  = draft.label,
        denom  = draft.denom,
        muted  = if (draft.muted) true else null,    // omit false (encodeDefaults=false)
        soloed = if (draft.soloed) true else null,
        items  = draft.items.map { serializeItem(it) },
        defaultSoundId = draft.defaultSoundId,
    )

    private fun serializeItem(item: TrackItem): RhyItem = when (item) {
        is TrackItem.Beat -> RhyItem(
            type    = "beat",
            num     = item.displayNum,
            den     = item.displayDenom,
            active  = if (!item.active) false else null,  // omit true (default)
            soundId = item.soundId,
            volume  = if (item.volume != 1.0f) item.volume else null,  // omit 1.0
        )
        is TrackItem.BracketOpen  -> RhyItem(type = "open")
        is TrackItem.BracketClose -> RhyItem(type = "close")
        is TrackItem.Repeat       -> RhyItem(type = "repeat", count = item.count)
        is TrackItem.Modulation   -> RhyItem(type = "mod", p = item.p, q = item.q)
        is TrackItem.SetBpm -> RhyItem(type = "setbpm", bpmVal = item.bpm)
    }

    // version 1.0 deserialization

    private fun deserializeV1(jsonString: String): TrackBuilderState {
        val dto = json.decodeFromString(RhyProject.serializer(), jsonString)

        val tracks = dto.tracks.mapIndexed { index, rhyTrack ->
            TrackDraft(
                id     = "track_$index",        // regenerate runtime ID
                label  = rhyTrack.label,
                denom  = rhyTrack.denom,
                muted  = rhyTrack.muted  ?: false,
                soloed = rhyTrack.soloed ?: false,
                items  = rhyTrack.items.mapNotNull { deserializeItem(it) },
                defaultSoundId = rhyTrack.defaultSoundId
            )
        }

        return TrackBuilderState(
            tracks           = tracks.ifEmpty { listOf(TrackBuilder.newTrackDraft(0)) },
            activeTrackIndex = 0,
            bpm              = dto.bpm,
            inputMode        = InputMode.Normal,
            playbackState    = PlaybackState.Stopped,
            cursorIndex      = null,
        )
    }

    private fun deserializeItem(item: RhyItem): TrackItem? = when (item.type) {
        "beat" -> {
            val num = item.num ?: return null
            val den = item.den ?: return null
            TrackItem.Beat(
                displayNum   = num,
                displayDenom = den,
                active       = item.active ?: true,
                soundId      = item.soundId,
                volume       = item.volume ?: 1.0f,
            )
        }
        "open"   -> TrackItem.BracketOpen
        "close"  -> TrackItem.BracketClose
        "repeat" -> item.count?.let { TrackItem.Repeat(it) }
        "mod"    -> {
            val p = item.p ?: return null
            val q = item.q ?: return null
            TrackItem.Modulation(p, q)
        }
        "setbpm" -> item.bpmVal?.let { TrackItem.SetBpm(it) }
        else -> null   // unknown type from newer version — skip gracefully
    }

    private fun currentIsoTimestamp(): String {
        val ms   = nanoNow() / 1_000_000L
        val s    = ms / 1000
        val sec  = (s % 60).toString().padStart(2, '0')
        val min  = ((s / 60) % 60).toString().padStart(2, '0')
        val hour = ((s / 3600) % 24).toString().padStart(2, '0')
        val day  = s / 86400
        return "day=${day} ${hour}:${min}:${sec} UTC"
    }
}

class DeserializationException(message: String) : Exception(message)

// json data transfer obj tyupe
// purely for serialization — not used in runtime model

@Serializable
private data class RhyProject(
    val version:   Int,
    val name:      String,
    val bpm:       Double,
    val createdAt: String    = "",
    val tracks:    List<RhyTrack>,
)

@Serializable
private data class RhyTrack(
    val label:  String,
    val denom:  Int,
    val muted:  Boolean?         = null,
    val soloed: Boolean?         = null,
    val items:  List<RhyItem>,
    val defaultSoundId: String? = null,
)

@Serializable
private data class RhyItem(
    val type:    String,
    // beat fields
    val num:     Int?    = null,
    val den:     Int?    = null,
    val active:  Boolean?= null,
    val soundId: String? = null,
    val volume:  Float?  = null,
    // repeat field
    val count:   Int?    = null,
    // modulation fields
    val p:       Int?    = null,
    val q:       Int?    = null,
    val bpmVal: Double? = null,
)