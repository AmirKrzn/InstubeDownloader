package com.example.videodl

import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var spinner: Spinner
    private lateinit var downloadBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var bar: ProgressBar
    private lateinit var status: TextView

    private val labels = arrayOf("۱۰۸۰ یا کمتر", "۷۲۰ یا کمتر", "۴۸۰ یا کمتر", "فقط صدا")
    private val heights = intArrayOf(1080, 720, 480, 0) // 0 = audio only

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val p = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(p, p, p, p)
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 18f; setPadding(0, p / 2, 0, p / 4) }

        root.addView(label("لینک‌ها را اینجا بچسبانید (هر لینک در یک خط)"))
        input = EditText(this).apply {
            minLines = 5; gravity = Gravity.TOP or Gravity.START; textSize = 16f
            setBackgroundColor(Color.parseColor("#F0F0F0")); setPadding(p, p, p, p)
            textDirection = View.TEXT_DIRECTION_LTR
        }
        root.addView(input)

        root.addView(label("کیفیت"))
        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(2) // default: 480
        }
        root.addView(spinner)

        downloadBtn = Button(this).apply { text = "دانلود"; textSize = 20f; setOnClickListener { startDownload() } }
        root.addView(downloadBtn)

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(bar)
        status = TextView(this).apply { textSize = 16f; setPadding(0, p / 2, 0, p / 2) }
        root.addView(status)

        updateBtn = Button(this).apply { text = "به‌روزرسانی yt-dlp"; setOnClickListener { update() } }
        root.addView(updateBtn)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun setBusy(b: Boolean) {
        downloadBtn.isEnabled = !b; updateBtn.isEnabled = !b
    }

    // YouTube now needs a JS runtime. The library (0.18.1+) bundles QuickJS; find it in the native lib dir.
    private fun jsRuntimeArg(): String? {
        val f = File(applicationInfo.nativeLibraryDir).listFiles()
            ?.firstOrNull { it.name.contains("qjs") || it.name.contains("quickjs") }
        return f?.let { "quickjs:${it.absolutePath}" }
    }

    // Auto-fill the box with a copied link when the app is opened/returned to
    private var lastClip = ""
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim() ?: return
        if (clip.startsWith("http") && clip != lastClip &&
            (input.text.isBlank() || input.text.toString() == lastClip)) {
            input.setText(clip); lastClip = clip
        }
    }

    private fun startDownload() {
        val urls = input.text.toString().split(Regex("\\s+")).filter { it.startsWith("http") }
            .map { if ("instagram.com" in it) it.substringBefore("?") else it } // drop ?img_index so ALL slides are read
        if (urls.isEmpty()) { status.text = "لینکی پیدا نشد"; return }
        val h = heights[spinner.selectedItemPosition]
        val js = jsRuntimeArg()
        val okExt = setOf("mp4", "mp3", "webm", "mkv", "m4a")
        setBusy(true); bar.progress = 0
        Thread {
            var ok = 0
            var lastError = ""
            urls.forEachIndexed { i, url ->
                val dir = File(cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
                val insta = "instagram.com" in url
                try {
                    val req = YoutubeDLRequest(url)
                    req.addOption("-o", "${dir.absolutePath}/%(title).60s [%(id)s].%(ext)s")
                    if (insta) req.addOption("--ignore-errors")   // multi-slide post: skip photo slides, keep videos
                    else req.addOption("--no-playlist")
                    if (js != null && !insta) req.addOption("--js-runtimes", js)
                    if (h == 0) {
                        req.addOption("-x")
                        req.addOption("--audio-format", "mp3")
                    } else {
                        req.addOption("-f", "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b")
                        req.addOption("--merge-output-format", "mp4")
                    }
                    YoutubeDL.getInstance().execute(req, "dl$i") { pr, _, _ ->
                        runOnUiThread {
                            bar.progress = pr.toInt().coerceIn(0, 100)
                            status.text = "در حال دانلود (${i + 1} از ${urls.size})…"
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message?.take(200) ?: ""
                }
                // keep whatever finished, even if some slides failed
                val files = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in okExt } ?: emptyList()
                try {
                    if (files.isNotEmpty()) { files.forEach { save(it, h == 0) }; ok++ }
                } catch (e: Exception) { lastError = e.message ?: "" }
            }
            runOnUiThread {
                setBusy(false)
                val err = if (lastError.contains("No video formats")) "ویدیویی در این لینک پیدا نشد" else lastError
                status.text = "تمام شد: $ok از ${urls.size}" +
                    (if (ok < urls.size) "\nخطا: $err" else "\nپوشه: Movies/Downloader")
            }
        }.start()
    }

    // Copy the finished file into the public Movies / Music folder so it shows in Gallery
    private fun save(f: File, audio: Boolean) {
        val mime = when (f.extension.lowercase()) {
            "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"; "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"; else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) "Music/Downloader" else "Movies/Downloader")
        }
        val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                         else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = contentResolver.insert(collection, values)!!
        contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
        f.delete()
    }

    private fun update() {
        setBusy(true); status.text = "در حال به‌روزرسانی…"
        Thread {
            val msg = try {
                val r = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.STABLE)
                if (r == YoutubeDL.UpdateStatus.DONE) "به‌روزرسانی انجام شد ✓" else "نسخه فعلی جدید است ✓"
            } catch (e: Exception) { "خطا در به‌روزرسانی (اینترنت را بررسی کنید)" }
            runOnUiThread { status.text = msg; setBusy(false) }
        }.start()
    }
}
