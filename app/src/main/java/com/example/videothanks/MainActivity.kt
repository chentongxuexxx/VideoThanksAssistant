package com.example.videothanks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 64, 40, 32)
        }
        val info = TextView(this).apply {
            text = "视频号感谢助手 v1.0\n\n1. 开启无障碍服务\n2. 打开微信 → 视频号 → 消息 → 关注\n3. 在悬浮栏点击‘扫描’查看目标\n4. 每次点击‘确认感谢’仅执行一次\n\n只处理微信前台页面，不会自动滚动。请先核对目标，避免误触。"
            textSize = 17f
        }
        layout.addView(info)
        val settings = Button(this).apply {
            text = "打开无障碍设置"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        layout.addView(settings)
        val reset = Button(this).apply {
            text = "重置本次计数"
            setOnClickListener {
                getSharedPreferences("stats", MODE_PRIVATE).edit().putInt("count", 0).apply()
                info.text = "本次计数已重置。\n\n返回微信继续使用悬浮栏。"
            }
        }
        layout.addView(reset)
        setContentView(layout)
    }
}