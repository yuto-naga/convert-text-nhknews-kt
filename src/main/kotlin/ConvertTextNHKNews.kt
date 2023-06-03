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
import java.io.File
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    try {
        // ログ初期化
        logInit()
        logger.info("**NHKニュースサイトから取得開始します**")

        val options = ChromeOptions()
        // 画面を描画しない
        options.addArguments("--headless")
        driver = RemoteWebDriver(URL("http://localhost:4444/wd/hub"), options)

        // アクセスランキングの記事URL一覧を取得
        val urlList = getUrls()

        // 記事一覧URLから記事内容を取得してリストに格納
        val articles = mutableListOf<Pair<String, String>>()
        urlList.forEachIndexed { index, url ->
            val (title, contexts) = getArticle(url, index + 1)
            // 記事の内容をテキストファイルに変換
            convertText(title, contexts)
        }


    } catch (e: Exception) {
        logger.error("${e.message}")
        logger.error("${e.stackTrace}")
    } finally {
        logger.info("**NHKニュースサイトから取得終了します**")
        // ブラウザを終了する
        driver.quit()
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
    val contexts = StringBuilder()

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
    contexts.appendLine("~~タイトル~~")
    val title = doc.selectFirst("h1")?.text()?.replace("\n", "") ?: ""
    contexts.appendLine(title)

    // サマリを抽出
    val summaryContents = doc.select(".content--summary, .content--summary-more")
    if (summaryContents.isNotEmpty()) {
        contexts.appendLine("~~要約~~")
        summaryContents.forEach { summary ->
            contexts.appendLine(convertPunctuation(summary.text()))
        }
    }

    // 本文を抽出
    val detailContents = doc.select(".body-title, .body-text")
    if (detailContents.isNotEmpty()) {
        contexts.appendLine("~~内容~~")
        detailContents.forEach { detailContent ->
            when (detailContent.attr("class")) {
                "body-title" -> contexts.appendLine(convertPunctuation(detailContent.text()))
                "body-text" -> contexts.appendLine(convertPunctuation(detailContent.text()))
                else -> logger.warn("本文から想定していないclassがありました ${detailContent.attr("class")}")
            }
        }
    }
    return Pair("${number}_$title", contexts.toString())
}

/** テキストファイル化 */
fun convertText(title: String, contexts: String) {
    logger.info("記事をテキストファイルに変換します。 タイトル: $title")
    val dateFormat = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm"))
    val dirPath = "$OUTPUT_BASE_DIRECTORY$dateFormat"
    val dir = File(dirPath)
    if (dir.exists() == false) {
        dir.mkdirs()
    }
//    articlePairs.forEachIndexed { index, articlePair ->
//        val (title, article) = articlePair
    val articleFile = File("$dirPath/$title.txt")
    articleFile.bufferedWriter().use { writer ->
        writer.write(contexts)
    }
//    }
}