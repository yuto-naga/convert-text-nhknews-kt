import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.util.StatusPrinter
import mu.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import resources.VoicevoxHelper
import resources.types.ArticleContextStatus
import resources.types.VoicevoxSpeakerType
import resources.utils.ExceptionUtil
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


/** NHK BASE URL */
const val NHK_BASE_URL = "https://www3.nhk.or.jp"

/** NHKアクセスランキングURL */
const val NHK_ACCESS_RANKING_URL = "$NHK_BASE_URL/news/ranking/access.html"

/** NHKソーシャルランキングURL */
const val NHK_SOCIAL_RANKING_URL = "$NHK_BASE_URL/news/ranking/social.html"

/** 記事タイトルから取得しないワードのリスト */
val NOT_INTEREST_WORDS = listOf("駅伝")

/** Output */
const val OUTPUT_BASE_DIRECTORY = "outputs/"
const val LOG_DIRECTORY = "logs"

private val logger = KotlinLogging.logger {}

private lateinit var driver: WebDriver

private lateinit var now: LocalDateTime

fun main(args: Array<String>) {
    now = LocalDateTime.now()
    // ログ初期化
    logInit()
    // スクレイピング
    val articles = mutableListOf<Pair<String, String>>()
    try {
        logger.info("**NHKニュースサイトから取得開始します**")
        val options = ChromeOptions()
        // 画面を描画しない
        options.addArguments("--headless")
        driver = RemoteWebDriver(URL("http://localhost:4444/wd/hub"), options)

        // アクセスランキングの記事URL一覧を取得
        val urlList = getUrls()

        // 記事一覧URLから記事内容を取得してリストに格納
        urlList.forEachIndexed { index, url ->
            articles.add(getArticle(url, index + 1))
        }
    } catch (e: Exception) {
        logger.error("${e.message}")
        val stackTraceStr = ExceptionUtil.getStackTrace(e)
        logger.error("$stackTraceStr")
    } finally {
        logger.info("**NHKニュースサイトから取得終了します**")
        // ブラウザを終了する
        driver.quit()
    }

    // テキスト・音声ファイル化
    logger.info("**${articles.size} 件の記事をテキスト・音声ファイルに変換します**")
    try {
        // 記事一覧URLから記事内容を取得してリストに格納
        articles.forEach { (title, context) ->
            // 記事の内容をテキストファイルに変換
            convertText(title, context)
            // 記事の内容を音声ファイルに変換
            convertAudioFile(title, context)
        }
    } catch (e: Exception) {
        logger.error("${e.message}")
        val stackTraceStr = ExceptionUtil.getStackTrace(e)
        logger.error("$stackTraceStr")
    } finally {
        logger.info("**テキスト・音声ファイルに変換を終了します**")
    }
}

/** 句読点があったら改行する */
fun convertPunctuation(value: String): String =
    value.replace(Regex("""[、。]"""), "$0\n")

/** ログの初期化 */
fun logInit() {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val fileAppender = FileAppender<ILoggingEvent>()
    fileAppender.context = context
    fileAppender.name = "FILE"
    val yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyyMM"))
    fileAppender.file = "$LOG_DIRECTORY/log_$yyyyMM.log"
    val encoder = PatternLayoutEncoder()
    encoder.context = context
    encoder.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    encoder.start()
    fileAppender.encoder = encoder
    fileAppender.start()
    val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME)
    rootLogger.addAppender(fileAppender)
    rootLogger.level = Level.INFO
    StatusPrinter.print(context)
}

/** タイトルに興味のないワードが含んでいるか */
fun isIncludeNotInterest(anchorElement: Element?): Boolean {
    val title = anchorElement?.select("em")?.first()?.text() ?: return false
    return NOT_INTEREST_WORDS.any { it.contains(title) }
}

/** 指定したURLからaタグ(記事URL)リストを取得 */
fun getAnchors(rankingUrl: String): Elements? {
    driver.get(rankingUrl)
    // 定義した上条件で待機する(最大20秒)
    val wait = WebDriverWait(driver, Duration.ofSeconds(20))
    // NAMEで指定したページ上の要素が読み込まれるまで待機
    wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("em")))
    val doc = Jsoup.parse(driver.pageSource)
    // アクセスランキングの範囲を対象に抽出
    val section = doc.select("section.content--items").first()
    return section?.select("a")
}

/** NHKのアクセスランキングからURLを取得する */
fun getUrls(): List<String> {
    logger.info("NHKソーシャル・アクセスランキングから記事URLを取得します")
    // ソーシャルランキングからaタグ(記事URL) を取得
    val socialAnchorList = getAnchors(NHK_SOCIAL_RANKING_URL)?.filterNot { isIncludeNotInterest(it) }
        ?.map { NHK_BASE_URL + it.attr("href") } ?: emptyList()
    // 連続してアクセスするので間隔を空ける
    Thread.sleep(3000)
    // アクセスランキングからaタグ(記事URL) を取得
    val accessAnchorList = getAnchors(NHK_ACCESS_RANKING_URL)?.filterNot { isIncludeNotInterest(it) }
        ?.map { NHK_BASE_URL + it.attr("href") } ?: emptyList()

    // フィルタしながらURL部分を抽出
    return (socialAnchorList + accessAnchorList).distinct()
}

/** 記事内容を取得する */
fun getArticle(targetUrl: String, number: Int): Pair<String, String> {
    logger.info("${number}番目の記事内容を取得します 記事URL: " + targetUrl)
    // 連続してアクセスするので間隔を空ける
    Thread.sleep(3000)

    // 結果を格納する記事内容
    val context = StringBuilder()

    driver.get(targetUrl)

    try {
        // 定義した上条件で待機する(最大10秒)
        val wait = WebDriverWait(driver, Duration.ofSeconds(10))
        wait.until(
            ExpectedConditions.or(
                ExpectedConditions.and(
                    ExpectedConditions.presenceOfElementLocated(By.tagName("h1")),
                    ExpectedConditions.presenceOfElementLocated(By.className("content--summary"))
                ),
                ExpectedConditions.presenceOfElementLocated(By.className("body-title"))
            )
        )
    } catch (e: TimeoutException) {
        logger.warn("${number}番目の指定した要素が取得できませんでした。該当の記事URLの取得を中止します。URL: " + targetUrl)
        return Pair("", "")
    }
    val doc = Jsoup.parse(driver.pageSource)

    // タイトルを抽出
    context.appendLine("~~タイトル~~")
    val title = doc.selectFirst("h1")?.text()?.replace("\n", "") ?: ""
    context.appendLine(title)

    // サマリを抽出
    val summaryContents = doc.select(".content--summary, .content--summary-more")
    if (summaryContents.isNotEmpty()) {
        context.appendLine("~~要約~~")
        summaryContents.forEach { summary ->
            context.appendLine(convertPunctuation(summary.text()))
        }
    }

    // 本文を抽出
    val detailContents = doc.select(".body-title, .body-text")
    if (detailContents.isNotEmpty()) {
        context.appendLine("~~内容~~")
        detailContents.forEach { detailContent ->
            when (detailContent.attr("class")) {
                "body-title" -> context.appendLine(convertPunctuation(detailContent.text()))
                "body-text" -> context.appendLine(convertPunctuation(detailContent.text()))
                else -> logger.warn("本文から想定していないclassがありました ${detailContent.attr("class")}")
            }
        }
    }
    return Pair("${number}_$title", context.toString())
}

/** テキストファイル化 */
fun convertText(title: String, context: String) {
    logger.info("記事をテキストファイルに変換します。 タイトル: $title")
    val dateFormat = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm"))
    val dirPath = "$OUTPUT_BASE_DIRECTORY$dateFormat"
    val dir = File(dirPath)
    if (dir.exists() == false) {
        dir.mkdirs()
    }
    val articleFile = File("$dirPath/$title.txt")
    articleFile.bufferedWriter().use { writer ->
        writer.write(context)
    }
}

/** wavファイル化 */
fun convertAudioFile(title: String, content: String) {
    logger.info("記事を音声ファイルに変換します。 タイトル: $title")
    val splitContents = splitLongString(content, 120)
    val audioBytesList = splitContents.map { (splitContent, status) ->
        // voicevox-apiから音声合成用のクエリを取得
        val query = VoicevoxHelper.createAudioQuery(
            URLEncoder.encode(splitContent, "UTF-8"),
            VoicevoxSpeakerType.四国めたん.ノーマル.id
        )
        // voicevox-apiから音声合成する
        val audioBytes = VoicevoxHelper.createSynthesis(query, VoicevoxSpeakerType.四国めたん.ノーマル.id)
        Base64.getEncoder().encodeToString(audioBytes)
    }
    val mergedAudioBytes = VoicevoxHelper.mergeWaves(audioBytesList)
    val dateFormat = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm"))
    val dirPath = "$OUTPUT_BASE_DIRECTORY$dateFormat/audio"
    val dir = File(dirPath)
    if (dir.exists() == false) {
        dir.mkdirs()
    }
    saveByteArrayToFile(mergedAudioBytes, "$dirPath/$title.wav")
}


/** ByteArrayをファイル化 */
fun saveByteArrayToFile(byteArray: ByteArray, filePath: String) {
    val file = File(filePath)
    val fileOutputStream = FileOutputStream(file)
    fileOutputStream.write(byteArray)
    fileOutputStream.close()
}

/**
 * 記事内容を特定の文字数で分割する
 * 方針: 改行ごとに区切った/結合要素が 引数:splitNum より大きい文字数になっても気にしない。おおよそで良いので。
 *
 * 1.改行ごとに区切る
 * 2.引数:splitNum以下であれば次の要素を結合する
 * 3. splitNumより文字数が大きくなるまで2を繰り返す
 * @param content 記事内容
 * @param splitNum 区切るおおよその文字数
 * @return List<Pair<区切った記事内容, 記事の状態>>
 */
fun splitLongString(content: String, splitNum: Int): List<Pair<String, Int>> {
    // まず改行で分割する
    val lines = content.split("\n")
    val result = mutableListOf<Pair<String, Int>>()

    var currentChunk = StringBuilder()

    for (line in lines) {
        val trimmedLine = line.trim()
        if (currentChunk.length <= splitNum) {
            // splitNum以下なら現在のチャンクに連結
            currentChunk.append(trimmedLine)
            currentChunk.append("\n") // 改行も連結
        } else {
            // splitNumを超える場合、現在のチャンクを結果に追加して新しいチャンクを作成
            if (currentChunk.isNotEmpty()) {
                result.add(currentChunk.toString().trim() to ArticleContextStatus.通常.intValue)
                currentChunk = StringBuilder(trimmedLine)
                currentChunk.append("\n")
            }
        }
    }
    // 最後のチャンクを追加
    if (currentChunk.isNotEmpty()) {
        result.add(currentChunk.toString().trim() to ArticleContextStatus.通常.intValue)
    }
    return result
}