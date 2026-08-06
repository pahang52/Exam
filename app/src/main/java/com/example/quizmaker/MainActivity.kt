package com.example.quizmaker

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JsResult
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.print.PrintAttributes
import android.print.PrintManager

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.allowFileAccess = true
        s.builtInZoomControls = true
        s.displayZoomControls = false

        webView.webViewClient = WebViewClient()

        // ✅ حل مشکل confirm/alert (حذف سوال)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message ?: "مطمئنی؟")
                    .setPositiveButton("بله") { _, _ -> result?.confirm() }
                    .setNegativeButton("خیر") { _, _ -> result?.cancel() }
                    .show()
                return true
            }
            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("باشه") { _, _ -> result?.confirm() }
                    .show()
                return true
            }
        }

        // ✅ پل بین JS و اندروید (برای PDF/Word/Print)
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun downloadBase64(b64: String, filename: String, mimeType: String) {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                saveToDownloads(filename, mimeType, bytes)
                runOnUiThread {
                    Toast.makeText(this@MainActivity,
                        "✅ ذخیره شد: $filename (در پوشه Downloads)",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity,
                        "خطا در ذخیره: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun doPrint() {
            runOnUiThread {
                val pm = getSystemService(PRINT_SERVICE) as PrintManager
                val jobName = "آزمون"
                val adapter = webView.createPrintDocumentAdapter(jobName)
                pm.print(jobName, adapter, PrintAttributes.Builder().build())
            }
        }
    }

    private fun saveToDownloads(filename: String, mimeType: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("نمی‌توان فایل را ساخت")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }
}
