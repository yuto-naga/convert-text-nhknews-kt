package resources

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*

class VoicevoxHelperTest {
    @Test
    fun createAudioQueryTest() {
        val result = VoicevoxHelper.createAudioQuery("りんご")
        assertNotNull(result)
        val audioBytes = VoicevoxHelper.createSynthesis(result)
        assertNotNull(audioBytes)
    }

    @Test
    fun margeWavesTest() {
        // クエリ作成
        val query1 = VoicevoxHelper.createAudioQuery("りんごりんごりんごりんごりんごりんごりんごりんご")
        val query2 = VoicevoxHelper.createAudioQuery("ごりらごりらごりらごりらごりらごりらごりらごりら")
        val query3 = VoicevoxHelper.createAudioQuery("らっぱらっぱらっぱらっぱらっぱらっぱらっぱらっぱ")

        // 音声合成作成
        val audioBytes1 = VoicevoxHelper.createSynthesis(query1)
        val audioBytes2 = VoicevoxHelper.createSynthesis(query2)
        val audioBytes3 = VoicevoxHelper.createSynthesis(query3)

        // 音声合成ファイルを結合
        val result =
            VoicevoxHelper.mergeWaves(
                listOf(
                    Base64.getEncoder().encodeToString(audioBytes1),
                    Base64.getEncoder().encodeToString(audioBytes2),
                    Base64.getEncoder().encodeToString(audioBytes3)
                )
            )
        assertNotNull(result)

        val file = File("test2.wav")
        file.writeBytes(result)
    }


    @Test
    fun 合成音声作成負荷テスト() {
        // 60文字程度の音声クエリを10個作成してマージしてもdockerコンテナが落ちないかの検証
        val template =
            "それは仕様です。それは仕様です。それは仕様です。それは仕様です。それは仕様です。それは仕様です。それは仕様です。それは仕様です。そ"
        val byteArrayStrings = (1..10).map {
            val query = VoicevoxHelper.createAudioQuery("${it}回目$template")
            val audioBytes = VoicevoxHelper.createSynthesis(query)
            Base64.getEncoder().encodeToString(audioBytes)
        }
        val result = VoicevoxHelper.mergeWaves(byteArrayStrings)
        val file = File("test3.wav")
        file.writeBytes(result)
    }
}