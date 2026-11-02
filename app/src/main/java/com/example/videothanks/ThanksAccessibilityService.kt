package com.example.videothanks

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ThanksAccessibilityService : AccessibilityService() {
    private var overlay: LinearLayout? = null
    private lateinit var wm: WindowManager
    private lateinit var status: TextView
    private var enabled = true
    private var lastClickAt = 0L
    private var lastBounds: Rect? = null
    private val wechat = "com.tencent.mm"

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No automatic click on events.
    }
    override fun onInterrupt() { enabled = false; if (::status.isInitialized) status.text = "已暂停" }

    private fun isWechatForeground(): Boolean {
        val root = wechatRoot() ?: return false
        return root.packageName?.toString() == wechat
    }

    private fun wechatRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active?.packageName?.toString() == wechat) return active
        return windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString() == wechat }?.root
    }

    private fun findThanks(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isVisibleToUser && (node.text?.toString()?.trim()?.contains("谢谢") == true || node.contentDescription?.toString()?.trim()?.contains("谢谢") == true)) {
                matches.add(node)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        visit(root)
        return matches.sortedWith(compareBy({ val r = Rect(); it.getBoundsInScreen(r); r.top }, { val r = Rect(); it.getBoundsInScreen(r); r.left }))
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            if (current?.isClickable == true && current?.isEnabled == true) return current
            current = current?.parent
        }
        return null
    }

    private fun scan() {
        if (!enabled) { status.text = "已暂停"; return }
        if (!isWechatForeground()) { status.text = "请先打开微信关注通知页"; return }
        val nodes = wechatRoot()?.let(::findThanks).orEmpty()
        status.text = if (nodes.isEmpty()) "未识别‘谢谢’：微信可能未提供无障碍文字节点" else "发现 ${nodes.size} 个含‘谢谢’的节点，请核对页面"
    }

    private fun confirmOne() {
        if (!enabled) { status.text = "已暂停"; return }
        if (!isWechatForeground()) { status.text = "当前不是微信，未操作"; return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickAt < 1500L) { status.text = "操作过快，请稍后确认"; return }
        val nodes = rootInActiveWindow?.let(::findThanks).orEmpty()
        if (nodes.isEmpty()) { status.text = "未找到‘谢谢’，请检查页面"; return }
        val target = nodes.firstOrNull { clickableAncestor(it) != null }
        if (target == null) { status.text = "按钮不可访问，未执行"; return }
        val rect = Rect().also { target.getBoundsInScreen(it) }
        if (lastBounds == rect && now - lastClickAt < 5000L) {
            status.text = "疑似相同目标，等待页面更新"
            return
        }
        val clicked = clickableAncestor(target)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (clicked) {
            lastClickAt = now
            lastBounds = rect
            val prefs = getSharedPreferences("stats", MODE_PRIVATE)
            val count = prefs.getInt("count", 0) + 1
            prefs.edit().putInt("count", count).apply()
            status.text = "已发送点击指令 $count 次（请核对页面结果）"
        } else status.text = "点击未执行，请手动处理"
    }

    private fun showOverlay() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.argb(235, 30, 40, 55))
        }
        status = TextView(this).apply {
            text = "感谢助手：等待操作"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        bar.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addButton(label: String, action: () -> Unit) {
            actions.addView(Button(this).apply {
                text = label
                textSize = 12f
                setOnClickListener { action() }
            })
        }
        addButton("扫描") { scan() }
        addButton("确认感谢") { confirmOne() }
        addButton("暂停/继续") { enabled = !enabled; status.text = if (enabled) "已继续" else "已暂停" }
        addButton("关闭") { removeOverlay() }
        bar.addView(actions)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 8; y = 160 }
        wm.addView(bar, params)
        overlay = bar
    }

    private fun removeOverlay() {
        overlay?.let { wm.removeView(it) }
        overlay = null
    }
    override fun onDestroy() { removeOverlay(); super.onDestroy() }
}