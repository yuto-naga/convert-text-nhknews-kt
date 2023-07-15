package resources

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object VoicevoxHelper {
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    private const val BASE_URL = "http://localhost:50021"

    // TODO スピーカーはめたん:ノーマル固定
    private val SPEAKER_NORMAL_METAN = "speaker" to "2"

    /**
     * 音声合成用のクエリを作成する
     * @param context クエリを作成するテキスト内容
     * @return クエリ(json形式)
     */
    fun createAudioQuery(context: String): String {
        val queryParams = mapOf(SPEAKER_NORMAL_METAN, "text" to context)
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
    fun createSynthesis(query: String): ByteArray {
        val queryParams = mapOf(SPEAKER_NORMAL_METAN)
        val queryString = queryParams.map { "${it.key}=${it.value}" }.joinToString("&")
        val urlWithParams = "${BASE_URL}/synthesis?$queryString"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(urlWithParams))
            .POST(HttpRequest.BodyPublishers.ofString(query))
            .header("Accept", "*/*")
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) {
            throw RuntimeException("${response.statusCode()} ${response.body()}")
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