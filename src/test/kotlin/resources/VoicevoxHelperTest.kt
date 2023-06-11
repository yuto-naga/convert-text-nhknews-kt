package resources

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class VoicevoxHelperTest {
    @Test
    fun createAudioQueryTest() {
        val result = VoicevoxHelper.createAudioQuery("これはテストです。テストテキスト")
        println(result)
        assertNotNull(result)

        val voiceFile = VoicevoxHelper.createSynthesis(result)
        println(voiceFile)

    }
}