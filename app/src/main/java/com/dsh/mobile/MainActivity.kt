package com.dsh.mobile

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient.FileChooserParams
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.dsh.mobile.databinding.ActivityMainBinding
import org.json.JSONObject

/**
 * DSH 手机端主界面：
 * - 首页提供「扫码连接 DeepSeek Harness」入口与手动输入地址。
 * - 扫码得到的配对链接（http://<局域网IP>:<port>/pair?token=... 或 https 隧道链接）
 *   在当前 WebView 中打开，完成配对后即从手机操控 DeepSeek。
 * - 本质上是一个继承取极轻的 WebView 浏览器。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var savedUrl: String? = null

    // 文件/图片选择回调（WebView 上传附件用）
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val text = result.data?.getStringExtra(ScanActivity.EXTRA_QR)
                if (!text.isNullOrBlank()) {
                    loadUrl(text)
                } else {
                    toast(R.string.toast_need_scan)
                }
            }
        }

    // 任务完成通知权限（Android 13+）。结果无需处理：未授权则通知静默跳过。
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback == null) return@registerForActivityResult
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uris: Array<Uri>? = data?.clipData?.let { clip ->
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                } ?: data?.data?.let { arrayOf(it) }
                callback.onReceiveValue(uris)
            } else {
                callback.onReceiveValue(null)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 16 / 由 targetSdk 强制 edge-to-edge：让内容避开系统栏，并保守地设置窗口。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        configureWebView()
        wireButtons()

        maybeRequestNotificationPermission()

        val lastUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREFS_LAST_URL, null)
        savedUrl = lastUrl
        if (!lastUrl.isNullOrBlank()) {
            // 重新加载上次会话页面（Cookie 已持久化），返回应用时不空白，且房子图标可正常用
            loadUrl(lastUrl)
        } else {
            showHome()
        }
    }

    // ---------------------------------------------------------------- WebView

    @Suppress("DEPRECATION")
    private fun configureWebView() {
        val wv = binding.webView
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(false) // target=_blank 在同一 WebView 内打开
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.setSupportZoom(false)
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.defaultTextEncodingName = "utf-8"

        // 持久化 Cookie / 会话，保证 DSH 会话在重开应用后仍保持
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        // JS 桥：供注入的网页「+」附件按钮触发原生触感反馈
        wv.addJavascriptInterface(DSHBridge(), "AndroidBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                // 顶栏标题 = 当前项目 / 会话名（即页面 <title>），空时保留默认应用名。
                if (!title.isNullOrBlank()) {
                    binding.projectTitle.text = title
                }
            }

            // 允许 Web 页「发送图片 / 文件」：拦截 <input type=file>，弹出系统文件选择器。
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val intent = try {
                    fileChooserParams?.createIntent()
                } catch (e: Exception) {
                    null
                } ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                saveLastUrl(url)
                updateNavState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // 在聊天页面注入「+」附件按钮（含文件选择 + 触感反馈）。
                view?.evaluateJavascript(ATTACH_JS, null)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReloaded: Boolean) {
                updateNavState()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                // 一切 http/https 都留在本 WebView；其余 scheme 忽略，避免跳出应用。
                return !(u.startsWith("http://") || u.startsWith("https://"))
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // 交由 WebView 展示错误页，不做破坏性处理
            }
        }
    }

    // ---------------------------------------------------------------- buttons

    private fun wireButtons() {
        tap(binding.btnBack) { if (binding.webView.canGoBack()) binding.webView.goBack() }
        tap(binding.btnForward) { if (binding.webView.canGoForward()) binding.webView.goForward() }
        tap(binding.btnReload) { binding.webView.reload() }
        tap(binding.btnHome) { binding.webView.evaluateJavascript("showSessionList()", null) }
        tap(binding.btnScan) { launchScanner() }
        tap(binding.btnScanConnect) { launchScanner() }
        tap(binding.btnConnect) { loadManualUrl() }

        binding.manualUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadManualUrl()
                true
            } else {
                false
            }
        }
    }

    /** 点击即触感反馈 + 执行动作。 */
    private fun tap(view: View, action: () -> Unit) {
        view.setOnClickListener {
            Haptics.light(this)
            action()
        }
    }

    private fun launchScanner() {
        scanLauncher.launch(Intent(this, ScanActivity::class.java))
    }

    private fun loadManualUrl() {
        loadUrl(binding.manualUrlInput.text?.toString() ?: "")
    }

    private fun loadUrl(raw: String) {
        val url = normalizeUrl(raw) ?: run {
            toast(R.string.toast_invalid_url)
            return
        }
        showBrowser()
        binding.webView.loadUrl(url)
        saveLastUrl(url)
        saveDshBase(url)
        updateNavState()
    }

    /** 记录 DSH 服务基址（scheme://host:port），供前台服务原生轮询会话状态用。 */
    private fun saveDshBase(url: String) {
        try {
            val u = Uri.parse(url)
            val host = u.host ?: return
            val port = if (u.port in 1..65535) ":${u.port}" else ""
            val base = "${u.scheme}://$host$port"
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREFS_DSH_BASE, base).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 补充协议前缀：局域网 DSH 通常为 http，隧道为 https。 */
    private fun normalizeUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        // 形如 192.168.1.10:3000 或 dsh.local → 视为 http 地址
        return if ((t.contains('.') || t.contains(':'))) "http://$t" else null
    }

    // ---------------------------------------------------------------- views

    private fun showBrowser() {
        binding.homeView.visibility = View.GONE
        binding.browserView.visibility = View.VISIBLE
        reveal(binding.browserView)
        updateNavState()
        // 进入网页/会话页：启动前台监控服务，保持进程存活，后台也能检测任务完成。
        startMonitorService()
    }

    private fun showHome() {
        binding.browserView.visibility = View.GONE
        binding.homeView.visibility = View.VISIBLE
        reveal(binding.homeView)
        binding.webView.stopLoading()
        // 回到首页：不再需要后台监控。
        stopMonitorService()
    }

    /** 淡入 + 轻微上移 + 轻微放大的揭示动效。 */
    private fun reveal(view: View) {
        view.alpha = 0f
        view.translationY = 20f * resources.displayMetrics.density
        view.scaleX = 0.98f
        view.scaleY = 0.98f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .start()
    }

    /** 「主页」返回已连接的 DSH 会话根地址；没有会话则回到连接页。 */
    /**
     * 依据系统状态栏 / 导航栏 insets 为内容预留内边距：
     * - 顶部避开状态栏，底部避开手势导航区，内容不再与系统栏重合或紧贴屏幕边缘；
     * - 额外留出 12dp 的上下呼吸空间，且随不同机型屏幕自适应；
     * - 软键盘弹出时底部按 IME 高度上抬，保证 DeepSeek 输入框不被键盘遮挡。
     */
    private fun applySystemBarInsets() {
        val root = binding.root
        val extra = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom) + extra
            v.setPadding(bars.left, bars.top + extra, bars.right, bottom)
            insets
        }
        root.requestApplyInsets()
    }

    private fun updateNavState() {
        binding.btnBack.isEnabled = binding.webView.canGoBack()
        binding.btnForward.isEnabled = binding.webView.canGoForward()
    }

    // ---------------------------------------------------------------- misc

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (binding.browserView.visibility == View.VISIBLE && binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun saveLastUrl(url: String?) {
        if (url.isNullOrBlank()) return
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREFS_LAST_URL, url).apply()
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------- notifications

    /** Android 13+ 需动态申请「通知」权限；未授权则通知功能静默降级。 */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** 在桌面端「任务完成」时发一条系统通知，点击回到主界面。 */
    private fun postTaskDoneNotification() {
        NotificationHelper.postTaskDone(this)
    }

    // ------------------------------------------------------------ keep-alive

    /** 启动前台监控服务：保持进程存活，让 WebView 注入脚本在后台仍能检测任务完成。 */
    private fun startMonitorService() {
        try {
            val intent = Intent(this, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // 启动前台服务失败不致命：前台时脚本仍能检测；仅后台通知可能失效。
        }
    }

    private fun stopMonitorService() {
        try {
            stopService(Intent(this, MonitorService::class.java))
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitorService()
    }

    /** 给注入的网页脚本调用的原生桥：提供触感反馈、提示与日志。 */
    private inner class DSHBridge {
        @JavascriptInterface
        fun vibrate(ms: Long) {
            Haptics.light(this@MainActivity)
        }

        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
        }

        /** 网页检测到 DeepSeek 回合完成（回复停止）时，发一条系统通知。 */
        @JavascriptInterface
        fun notifyTaskDone() {
            runOnUiThread { postTaskDoneNotification() }
        }

        /** 选图后弹出文字输入框，用户填写描述，确定后回传页面发送。 */
        @JavascriptInterface
        fun promptForText() {
            runOnUiThread {
                val view = layoutInflater.inflate(R.layout.dialog_send_image, null)
                val captionInput = view.findViewById<EditText>(R.id.captionInput)
                val btnSend = view.findViewById<TextView>(R.id.btnSend)
                val btnCancel = view.findViewById<TextView>(R.id.btnCancel)

                val dialog = Dialog(this@MainActivity)
                dialog.setContentView(view)
                dialog.setOnCancelListener {
                    binding.webView.evaluateJavascript("window.__dshCancelSend()", null)
                }
                btnSend.setOnClickListener {
                    val text = captionInput.text?.toString().orEmpty()
                    dialog.dismiss()
                    binding.webView.evaluateJavascript(
                        "window.__dshSendWithText(" + JSONObject.quote(text) + ")", null
                    )
                }
                btnCancel.setOnClickListener {
                    dialog.dismiss()
                    binding.webView.evaluateJavascript("window.__dshCancelSend()", null)
                }

                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.show()
                val dm = resources.displayMetrics
                dialog.window?.setLayout((dm.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                captionInput.requestFocus()
            }
        }
    }

    companion object {
        private const val PREFS = "dsh_mobile"
        private const val PREFS_LAST_URL = "last_url"
        private const val PREFS_DSH_BASE = "dsh_base"

        /**
         * 注入到 DSH 聊天页的脚本：
         * - 在对话框（输入区）内注入醒目的「+」按钮；
         * - 点击「+」触发隐藏的 <input type=file>，经 WebView onShowFileChooser 选图；
         * - 选中后把文件放入 DataTransfer，并向 document 派发 drop 事件 —— DeepSeek 前端
         *   在 document 上监听 drop 来收图（ComposerAttachments.onDrop），从而把图片附到会话
         *   （与电脑版流程一致）。
         */
        private const val ATTACH_JS = """
(function () {
  try {
    if (window.__dshAttachInit) return;
    window.__dshAttachInit = true;

    var BTN_ID = '__dsh_attach_plus__';
    var FP_ID = '__dsh_file_input__';
    var pendingFile = null;

    function ensureFileInput() {
      var inp = document.getElementById(FP_ID);
      if (!inp) {
        inp = document.createElement('input');
        inp.type = 'file';
        inp.id = FP_ID;
        inp.accept = 'image/*';
        inp.style.cssText = 'position:fixed;left:-9999px;top:-9999px;';
        inp.onchange = function () {
          vibrate(16);
          var file = inp.files && inp.files[0];
          if (!file) { toast('选图后未读取到文件'); return; }
          pendingFile = file;
          if (window.AndroidBridge && window.AndroidBridge.promptForText) {
            window.AndroidBridge.promptForText();
          } else {
            sendImageViaPrompt(file, '');
          }
        };
        (document.body || document.documentElement).appendChild(inp);
      }
      return inp;
    }

    function findComposerInput() {
      return document.querySelector(
        'textarea, [contenteditable="true"], [contenteditable="plaintext-only"], [role="textbox"]'
      );
    }

    // 找到承载输入框的横向 flex 容器；「+」按钮作为其第一个子元素（占位，不遮挡输入框）
    function findFlexRow(input) {
      var node = input;
      for (var i = 0; i < 5 && node && node.parentElement; i++) {
        node = node.parentElement;
        var st = window.getComputedStyle(node);
        var d = st.display;
        var dir = st.flexDirection || 'row';
        if ((d === 'flex' || d === 'inline-flex') && dir.indexOf('column') === -1) {
          return node;
        }
      }
      return null;
    }

    function vibrate(ms) {
      try {
        if (window.AndroidBridge && window.AndroidBridge.vibrate) window.AndroidBridge.vibrate(ms);
      } catch (e) {}
    }

    function toast(msg) {
      try {
        if (window.AndroidBridge && window.AndroidBridge.toast) window.AndroidBridge.toast(msg);
      } catch (e) {}
    }

    // 从多种来源解析当前会话 id：资源请求 / location / session.list RPC
    async function getSessionId() {
      try {
        // 手机网页当前打开的会话 id（最准确）
        try { if (typeof activeSession !== 'undefined' && activeSession) return String(activeSession); } catch (e) {}
        var res = performance.getEntriesByType('resource');
        for (var i = 0; i < res.length; i++) {
          var u = res[i].name || '';
          var m = u.match(/sessionId=([A-Za-z0-9._%-]+)/);
          if (m) return decodeURIComponent(m[1]);
        }
        var l = (location.href || '').match(/sessionId=([A-Za-z0-9._-]+)/);
        if (l) return decodeURIComponent(l[1]);
        // 兜底：走 session.list，取最近更新的会话
        var r = await window.fetch('/api/rpc', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ method: 'session.list', payload: {} })
        });
        var j = await r.json();
        var items = (j && j.value && j.value.items) || [];
        var best = null, bestT = -1;
        for (var k = 0; k < items.length; k++) {
          var it = items[k];
          var t = (it && it.updatedAt) || 0;
          if (t > bestT) { bestT = t; best = it; }
        }
        if (best && best.sessionId) return best.sessionId;
      } catch (e) {}
      return null;
    }

    function readAsDataURL(file) {
      return new Promise(function (resolve, reject) {
        var r = new FileReader();
        r.onload = function () { resolve(r.result); };
        r.onerror = function () { reject(new Error('读取图片失败')); };
        r.readAsDataURL(file);
      });
    }

    function decodeImage(dataUrl) {
      return new Promise(function (resolve, reject) {
        var img = new Image();
        img.onload = function () { resolve(img); };
        img.onerror = function (e) { reject(new Error('图片解码失败: ' + String(e && e.message || e))); };
        img.src = dataUrl;
      });
    }

    // 把图片压缩成 JPEG，并迭代降到 base64 <= 56KB（桥接 /api/rpc 上限 64KB）
    async function compressToFit(file) {
      var dataUrl = await readAsDataURL(file);
      var img = await decodeImage(dataUrl);
      var name = (file.name && file.name !== '')
        ? file.name.replace(/\.[a-z0-9]+$/i, '') + '.jpg'
        : 'image.jpg';
      var maxDim = 1024;
      var quads = [0.8, 0.7, 0.6, 0.5, 0.4, 0.3];
      var best = null;
      for (var iter = 0; iter < 5; iter++) {
        for (var qi = 0; qi < quads.length; qi++) {
          var maxW = img.naturalWidth || img.width || 1;
          var maxH = img.naturalHeight || img.height || 1;
          var scale = Math.min(1, maxDim / Math.max(maxW, maxH));
          var w = Math.max(1, Math.round(maxW * scale));
          var h = Math.max(1, Math.round(maxH * scale));
          var canvas = document.createElement('canvas');
          canvas.width = w; canvas.height = h;
          var ctx = canvas.getContext('2d');
          if (!ctx) continue;
          ctx.drawImage(img, 0, 0, w, h);
          var dataUrl2 = canvas.toDataURL('image/jpeg', quads[qi]);
          var b64 = dataUrl2.split(',')[1] || '';
          if (b64.length > 0 && b64.length <= 56000) { best = { b64: b64, mime: 'image/jpeg', name: name }; break; }
        }
        if (best) break;
        maxDim = Math.round(maxDim * 0.72);
      }
      if (!best) {
        var c2 = document.createElement('canvas'); c2.width = 40; c2.height = 40;
        var ctx2 = c2.getContext('2d');
        if (ctx2) {
          ctx2.drawImage(img, 0, 0, 40, 40);
          var d2 = c2.toDataURL('image/jpeg', 0.35);
          best = { b64: d2.split(',')[1] || '', mime: 'image/jpeg', name: name };
        }
      }
      if (!best) throw new Error('无法压缩到 64KB 以内');
      return best;
    }

    // 直接走 Harness 自有接口：POST /api/rpc 调 session.prompt，带图片内容块，绕过网页收图 UI
    async function sendImageViaPrompt(file, text) {
      try {
        var sid = await getSessionId();
        if (!sid) { toast('未找到会话，请重新连接后重试'); return; }
        var out = await compressToFit(file);
        var content = [{ type: 'image', data: out.b64, mediaType: out.mime, name: out.name }];
        if (text && String(text).trim() !== '') content.push({ type: 'text', text: text });
        var body = JSON.stringify({ method: 'session.prompt', payload: { sessionId: sid, mode: 'queue', content: content } });
        var resp = await window.fetch('/api/rpc', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: body
        });
        var txt = await resp.text();
        if (resp.ok) toast('图片已发送');
        else toast('发送失败');
      } catch (e) {
        toast('发送失败');
      }
    }

    // 用户在原生对话框里填好文字后，原生回调此处发送
    window.__dshSendWithText = function (text) {
      var f = pendingFile;
      pendingFile = null;
      if (f) sendImageViaPrompt(f, text || '');
      else toast('没有待发送的图片');
    };
    window.__dshCancelSend = function () {
      pendingFile = null;
    };

    function createButton() {
      if (document.getElementById(BTN_ID)) return;
      var input = findComposerInput();
      if (!input) return;
      var container = findFlexRow(input);
      if (!container) return; // 找不到横向输入行则不注入，避免遮挡

      var b = document.createElement('button');
      b.id = BTN_ID;
      b.type = 'button';
      b.innerHTML = '+';
      b.setAttribute('aria-label', '添加图片');
      b.style.cssText = [
        'flex:0 0 auto',
        'width:38px', 'height:38px',
        'margin-right:8px',
        'border-radius:50%', 'border:none', 'padding:0',
        'display:flex', 'align-items:center', 'justify-content:center',
        'font-size:24px', 'font-weight:600', 'line-height:1',
        'cursor:pointer', 'user-select:none', '-webkit-user-select:none',
        'align-self:center',
        'background:linear-gradient(135deg,#6F86FF,#4D6BFE)', 'color:#fff',
        'box-shadow:0 2px 8px rgba(77,107,254,.4)',
        'transition:transform .12s ease'
      ].join(';');
      b.onclick = function (ev) {
        ev.preventDefault(); ev.stopPropagation();
        vibrate(22);
        b.style.transform = 'scale(0.82)';
        setTimeout(function () { b.style.transform = 'scale(1)'; }, 140);
        ensureFileInput().click();
      };
      b.style.animation = 'dshPop .4s cubic-bezier(.34,1.56,.64,1)';
      if (!document.getElementById('dshPopStyle')) {
        var style = document.createElement('style');
        style.id = 'dshPopStyle';
        style.textContent = '@keyframes dshPop{0%{transform:scale(0);opacity:0}100%{transform:scale(1);opacity:1}}';
        (document.head || document.documentElement).appendChild(style);
      }
      container.insertBefore(b, container.firstChild);
    }

    function install() {
      if (!findComposerInput()) return;
      createButton();
    }

    install();
    setInterval(install, 1000);

    // ---------- 任务完成通知 ----------
    // 直接轮询 DSH 自有接口 /api/rpc session.list（每个会话带 running 布尔，
    // 表示该会话的 Agent 是否正在跑），捕捉「running=true → false」的转换，
    // 即认为一次 DeepSeek 回复完成。比读页面 DOM 更可靠，不受页面结构变化影响。
    var dshWasRunning = null;
    function dshFetchActiveRunning() {
      return new Promise(function (resolve) {
        try {
          window.fetch('/api/rpc', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ method: 'session.list', payload: {} })
          }).then(function (r) { return r.json(); }).then(function (j) {
            var items = (j && j.value && j.value.items) || [];
            var sid = null;
            try { if (typeof activeSession !== 'undefined' && activeSession) sid = String(activeSession); } catch (e) {}
            var target = null;
            for (var i = 0; i < items.length; i++) { if (items[i].sessionId === sid) { target = items[i]; break; } }
            if (!target) for (var i = 0; i < items.length; i++) { if (items[i].running) { target = items[i]; break; } }
            if (!target && items.length) target = items[0];
            resolve(target ? !!target.running : null);
          }).catch(function () { resolve(null); });
        } catch (e) { resolve(null); }
      });
    }
    function dshPollTaskDone() {
      dshFetchActiveRunning().then(function (running) {
        if (running === null) return;
        if (dshWasRunning === true && running === false) {
          dshWasRunning = false;
          if (window.AndroidBridge && window.AndroidBridge.notifyTaskDone) {
            window.AndroidBridge.notifyTaskDone();
          }
        } else {
          dshWasRunning = running;
        }
      });
    }
    setInterval(dshPollTaskDone, 1200);
  } catch (e) {}
})();
"""
    }
}
