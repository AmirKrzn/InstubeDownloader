package com.example.videodl

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var spinner: Spinner
    private lateinit var downloadBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var openBtn: Button
    private lateinit var bar: ProgressBar
    private lateinit var status: TextView

    private val labels = arrayOf("۱۰۸۰ یا کمتر", "۷۲۰ یا کمتر", "۴۸۰ یا کمتر", "فقط صدا")
    private val heights = intArrayOf(1080, 720, 480, 0) // 0 = audio only
    private val saved = CopyOnWriteArrayList<Pair<Uri, String>>()
    private var lastClip = ""

    private val indigo = Color.parseColor("#4F46E5")
    private val green = Color.parseColor("#16A34A")
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat(); setColor(color)
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun styledBtn(text: String, bg: Int, fg: Int, size: Float = 18f, stroke: Int = 0) =
        Button(this).apply {
            this.text = text; textSize = size; isAllCaps = false; setTextColor(fg)
            background = rounded(bg, stroke); stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(10) }
        }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 17f; setTextColor(Color.parseColor("#1E293B")); setPadding(0, dp(14), 0, dp(6))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#3730A3")
        val p = dp(16)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }
        outer.addView(TextView(this).apply {
            text = "دانلود ویدیو"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setBackgroundColor(indigo)
            setPadding(p, dp(18), p, dp(18))
        }, LinearLayout.LayoutParams(match, wrap))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p, p, p) }
        outer.addView(ScrollView(this).apply { addView(content) })

        content.addView(label("لینک‌ها را اینجا بچسبانید (هر لینک در یک خط)"))
        input = EditText(this).apply {
            minLines = 4; gravity = Gravity.TOP or Gravity.START; textSize = 16f
            background = rounded(Color.WHITE, Color.parseColor("#CBD5E1")); setPadding(p, p, p, p)
            textDirection = View.TEXT_DIRECTION_LTR; hint = "https://"
        }
        content.addView(input)

        clearBtn = styledBtn("✕  پاک کردن", Color.WHITE, Color.parseColor("#475569"), 14f, Color.parseColor("#CBD5E1")).apply {
            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(6) }
            setOnClickListener {
                input.setText(""); status.visibility = View.GONE
                openBtn.visibility = View.GONE; bar.progress = 0
            }
        }
        content.addView(clearBtn)

        content.addView(label("کیفیت"))
        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(2) // default: 480
        }
        content.addView(LinearLayout(this).apply {
            background = rounded(Color.WHITE, Color.parseColor("#CBD5E1"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(spinner, LinearLayout.LayoutParams(match, wrap))
        })

        downloadBtn = styledBtn("دانلود", indigo, Color.WHITE, 20f).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(18)
            setOnClickListener { onDownloadClicked() }
        }
        content.addView(downloadBtn)

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(indigo)
        }
        content.addView(bar, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(12) })

        status = TextView(this).apply { textSize = 16f; setPadding(dp(14), dp(12), dp(14), dp(12)); visibility = View.GONE }
        content.addView(status, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(8) })

        openBtn = styledBtn("▶  باز کردن ویدیو", green, Color.WHITE, 18f).apply {
            visibility = View.GONE; setOnClickListener { openSaved() }
        }
        content.addView(openBtn)

        updateBtn = styledBtn("به‌روزرسانی yt-dlp", Color.parseColor("#E2E8F0"), Color.parseColor("#334155"), 15f).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(28)
            setOnClickListener { update() }
        }
        content.addView(updateBtn)

        setContentView(outer)
    }

    private fun setStatus(t: String, kind: Int) {
        val (bg, fg) = when (kind) {
            1 -> "#DCFCE7" to "#166534"   // success
            2 -> "#FEE2E2" to "#991B1B"   // error
            else -> "#E0E7FF" to "#312E81" // info
        }
        status.text = t; status.setTextColor(Color.parseColor(fg))
        status.background = rounded(Color.parseColor(bg)); status.visibility = View.VISIBLE
    }

    private fun setBusy(b: Boolean) {
        downloadBtn.isEnabled = !b; updateBtn.isEnabled = !b; clearBtn.isEnabled = !b
    }

    // YouTube needs a JS runtime; the library (0.18.1+) bundles QuickJS in its native lib dir.
    private fun jsRuntimeArg(): String? {
        val f = File(applicationInfo.nativeLibraryDir).listFiles()
            ?.firstOrNull { it.name.contains("qjs") || it.name.contains("quickjs") }
        return f?.let { "quickjs:${it.absolutePath}" }
    }

    // Auto-fill the box with a copied link when the app is opened / returned to
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

    private fun parseUrls() = input.text.toString().split(Regex("\\s+")).filter { it.startsWith("http") }
        .map { if ("instagram.com" in it) it.substringBefore("?") else it } // drop ?img_index so ALL slides are read

    private fun baseRequest(url: String): YoutubeDLRequest {
        val req = YoutubeDLRequest(url)
        val insta = "instagram.com" in url
        if (insta) req.addOption("--ignore-errors") else req.addOption("--no-playlist")
        if (!insta) jsRuntimeArg()?.let { req.addOption("--js-runtimes", it) }
        return req
    }

    private fun loadBitmap(url: String?): Bitmap? = try {
        if (url == null) null else URL(url).openStream().use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    // Step 1: fetch title + thumbnail of the first link, then ask for confirmation
    private fun onDownloadClicked() {
        val urls = parseUrls()
        if (urls.isEmpty()) { setStatus("لینکی پیدا نشد", 2); return }
        setBusy(true); setStatus("در حال بررسی لینک…", 0)
        Thread {
            var title: String? = null
            var thumb: Bitmap? = null
            try {
                val info = YoutubeDL.getInstance().getInfo(baseRequest(urls[0]))
                title = info.title
                thumb = loadBitmap(info.thumbnail)
            } catch (e: Exception) { /* preview is optional */ }
            runOnUiThread { setBusy(false); status.visibility = View.GONE; showConfirm(urls, title, thumb) }
        }.start()
    }

    private fun showConfirm(urls: List<String>, title: String?, thumb: Bitmap?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        if (thumb != null) box.addView(ImageView(this).apply {
            setImageBitmap(thumb); scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(match, dp(200)))
        box.addView(TextView(this).apply {
            text = (title ?: "ویدیو") + if (urls.size > 1) "\n(و ${urls.size - 1} لینک دیگر)" else ""
            textSize = 17f; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        })
        AlertDialog.Builder(this)
            .setTitle("دانلود شود؟")
            .setView(box)
            .setPositiveButton("دانلود") { _, _ -> startDownload(urls) }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // Step 2: the actual download
    private fun startDownload(urls: List<String>) {
        val h = heights[spinner.selectedItemPosition]
        val okExt = setOf("mp4", "mp3", "webm", "mkv", "m4a")
        setBusy(true); bar.progress = 0; saved.clear()
        openBtn.visibility = View.GONE
        setStatus("در حال دانلود…", 0)
        Thread {
            var ok = 0
            var lastError = ""
            urls.forEachIndexed { i, url ->
                val dir = File(cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
                try {
                    val req = baseRequest(url)
                    req.addOption("-o", "${dir.absolutePath}/%(title).60s [%(id)s].%(ext)s")
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
                    lastError = e.message ?: ""
                }
                // keep whatever finished, even if some slides failed
                val files = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in okExt } ?: emptyList()
                try {
                    if (files.isNotEmpty()) { files.forEach { saved.add(save(it, h == 0)) }; ok++ }
                } catch (e: Exception) { lastError = e.message ?: "" }
            }
            runOnUiThread {
                setBusy(false)
                if (ok > 0) {
                    val msg = if (h == 0) "دانلود موفقیت آمیز بود، فایل صوتی ذخیره شد"
                              else "دانلود موفقیت آمیز بود، ویدیو به گالری شما افزوده شد"
                    setStatus(msg + if (ok < urls.size) "\n($ok از ${urls.size} لینک)" else "", 1)
                    openBtn.text = if (h == 0) "▶  پخش فایل" else "▶  باز کردن ویدیو"
                    openBtn.visibility = View.VISIBLE
                } else {
                    setStatus("خطا: " + friendlyError(lastError), 2)
                }
            }
        }.start()
    }

    private fun friendlyError(raw: String): String = when {
        raw.contains("Sign in to confirm") -> "یوتیوب دانلود را مسدود کرد. سرور VPN را عوض کنید و دوباره امتحان کنید."
        raw.contains("429") -> "یوتیوب موقتاً محدودیت گذاشته. چند دقیقه صبر کنید یا VPN را عوض کنید."
        raw.contains("No video formats") -> "ویدیویی در این لینک پیدا نشد."
        raw.isBlank() -> "دانلود انجام نشد."
        else -> raw.lines().lastOrNull { it.startsWith("ERROR") }?.take(160) ?: raw.take(160)
    }

    // Copy the finished file into the public Movies / Music folder so it shows in Gallery
    private fun save(f: File, audio: Boolean): Pair<Uri, String> {
        val mime = when (f.extension.lowercase()) {
            "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "mp4" -> "video/mp4"; "webm" -> "video/webm"
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
        return uri to mime
    }

    private fun openSaved() {
        val (uri, mime) = saved.firstOrNull() ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            Toast.makeText(this, "برنامه‌ای برای پخش پیدا نشد", Toast.LENGTH_LONG).show()
        }
    }

    private fun update() {
        setBusy(true); setStatus("در حال به‌روزرسانی…", 0)
        Thread {
            val res = try {
                val r = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.STABLE)
                (if (r == YoutubeDL.UpdateStatus.DONE) "به‌روزرسانی انجام شد ✓" else "نسخه فعلی جدید است ✓") to 1
            } catch (e: Exception) { "خطا در به‌روزرسانی (اینترنت را بررسی کنید)" to 2 }
            runOnUiThread { setStatus(res.first, res.second); setBusy(false) }
        }.start()
    }
}
