package resources.types

/** Voicevox読み上げ者(一部) */
sealed class VoicevoxSpeakerType(val id: Int) {
    object 四国めたん {
        object ノーマル : VoicevoxSpeakerType(2)
        object あまあま : VoicevoxSpeakerType(0)
        object ツンツン : VoicevoxSpeakerType(6)
        object セクシー : VoicevoxSpeakerType(4)
        object ささやき : VoicevoxSpeakerType(36)
        object ヒソヒソ : VoicevoxSpeakerType(37)
    }

    object ずんだもん {
        object ノーマル : VoicevoxSpeakerType(3)
        object あまあま : VoicevoxSpeakerType(1)
        object ツンツン : VoicevoxSpeakerType(7)
        object セクシー : VoicevoxSpeakerType(5)
        object ささやき : VoicevoxSpeakerType(22)
        object ヒソヒソ : VoicevoxSpeakerType(38)
    }

    // TODO speaker追加
}

