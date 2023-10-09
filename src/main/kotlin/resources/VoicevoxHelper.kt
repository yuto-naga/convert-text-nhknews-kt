package resources

import com.fasterxml.jackson.databind.ObjectMapper
import resources.types.VoicevoxSpeakerType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


object VoicevoxHelper {
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    private const val BASE_URL = "http://localhost:50021"

    /**
     * 音声合成用のクエリを作成する
     * @param context クエリを作成するテキスト内容
     * @return クエリ(json形式)
     */
    fun createAudioQuery(
        context: String,
        speakerType: VoicevoxSpeakerType = VoicevoxSpeakerType.四国めたん.ノーマル
    ): String {
        val queryParams = mapOf("speaker" to speakerType.id, "text" to context)
        val queryString = queryParams.map { "${it.key}=${it.value}" }.joinToString("&")
        val urlWithParams = "${BASE_URL}/audio_query?$queryString"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(urlWithParams))
            .POST(HttpRequest.BodyPublishers.noBody())
            .header("Accept", "*/*")
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("${response.statusCode()} ${response.body()}")
        }
        return response.body()
    }


    /**
     * 音声合成する
     * @param query クエリ(json)
     * @return byteArray
     */
    fun createSynthesis(
        query: String,
        speakerType: VoicevoxSpeakerType = VoicevoxSpeakerType.四国めたん.ノーマル
    ): ByteArray {
        val queryParams = mapOf("speaker" to speakerType.id)
        val queryString = queryParams.map { "${it.key}=${it.value}" }.joinToString("&")
        val urlWithParams = "${BASE_URL}/synthesis?$queryString"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(urlWithParams))
            .POST(HttpRequest.BodyPublishers.ofString(query))
            .header("Accept", "*/*")
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) {
            throw RuntimeException("${response.statusCode()} ${response.body().decodeToString()}")
        }
        val headers = response.headers()
        val contentType: String = headers.firstValue("Content-Type").orElse("")

        if (contentType == "audio/wav") {
            return response.body()
        } else {
            throw RuntimeException("Received content is not of type audio/wav.")
        }
    }

    /**
     * base64エンコードされた複数のwavデータを一つに結合する
     * @param waves wavデータ
     */
    fun mergeWaves(waves: List<String>): ByteArray {
        val url = "${BASE_URL}/connect_waves"
        val jsonBody = ObjectMapper().writeValueAsString(waves)
        val request = HttpRequest.newBuilder().uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Accept", "*/*")
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) {
            throw RuntimeException("${response.statusCode()} ${response.body().decodeToString()}")
        }
        val headers = response.headers()
        val contentType: String = headers.firstValue("Content-Type").orElse("")

        if (contentType == "audio/wav") {
            return response.body()
        } else {
            throw RuntimeException("Received content is not of type audio/wav.")
        }
    }
}