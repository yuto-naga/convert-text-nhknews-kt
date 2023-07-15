package resources

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class VoicevoxHelperTest {
    @Test
    fun createAudioQueryTest() {
        val result = VoicevoxHelper.createAudioQuery("りんご")
        assertNotNull(result)
        val audioBytes = VoicevoxHelper.createSynthesis(result)
        assertNotNull(audioBytes)
    }

}