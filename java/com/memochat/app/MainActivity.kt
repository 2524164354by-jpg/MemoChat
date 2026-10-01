package com.memochat.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.memochat.app.databinding.ActivityMainBinding
import com.memochat.app.ui.chatlist.ChatListFragment
import com.memochat.app.ui.plaza.PlazaFragment
import com.memochat.app.ui.settings.SettingsFragment
import com.memochat.app.util.Mode
import com.memochat.app.util.Prefs
import com.memochat.app.util.ThemeHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.memochat.app.ui.components.NewEntryDialog
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var chatFragment = ChatListFragment()
    private var plazaFragment = PlazaFragment()
    private var starredFragment = com.memochat.app.ui.star.StarredFragment()
    private var settingsFragment = SettingsFragment()

    private var currentFragment: Fragment? = null

    private val pickEntryAvatar =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            NewEntryDialog.onPicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(applicationContext)
        ThemeHelper.apply(Prefs.mode)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!getSharedPreferences("onb", MODE_PRIVATE).getBoolean("seen", false)) {
            startActivity(android.content.Intent(this, com.memochat.app.ui.onboarding.OnboardingActivity::class.java))
        }

        applyRootBackground()
        setupBottomNav()
        seedTestData()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            private var backPressedOnce = 0L
            override fun handleOnBackPressed() {
                if (chatFragment.isAdded && chatFragment.isSelectionMode()) {
                    chatFragment.exitSelection()
                    return
                }
                if (currentTab() != TAB_CHAT) {
                    switchTo(TAB_CHAT)
                    return
                }
                if (System.currentTimeMillis() - backPressedOnce < 2000) {
                    finish()
                } else {
                    backPressedOnce = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, "再按一次退出微记", Toast.LENGTH_SHORT).show()
                }
            }
        })

        if (savedInstanceState == null) {
            showFragment(chatFragment, animated = false)
        } else {
            val tag = savedInstanceState.getString(KEY_TAB) ?: TAB_CHAT
            // On recreate, fragments are restored by FragmentManager.
            // Re-bind our field references to the restored instances.
            val fm = supportFragmentManager
            chatFragment = fm.findFragmentById(R.id.fragment_container) as? com.memochat.app.ui.chatlist.ChatListFragment
                ?: com.memochat.app.ui.chatlist.ChatListFragment()
            plazaFragment = fm.findFragmentByTag("plaza") as? com.memochat.app.ui.plaza.PlazaFragment
                ?: com.memochat.app.ui.plaza.PlazaFragment()
            settingsFragment = fm.findFragmentByTag("settings") as? com.memochat.app.ui.settings.SettingsFragment
                ?: com.memochat.app.ui.settings.SettingsFragment()
            currentFragment = when (tag) {
                TAB_STATS -> plazaFragment
                TAB_SETTINGS -> settingsFragment
                else -> chatFragment
            }
            // Hide all fragments except the current one
            val tx = fm.beginTransaction()
            listOf(chatFragment, plazaFragment, settingsFragment).forEach {
                if (it.isAdded && it !== currentFragment) tx.hide(it)
            }
            val cf = currentFragment
            if (cf != null && cf.isAdded) tx.show(cf)
            tx.commitNowAllowingStateLoss()
            switchTabUi(tag)
        }

        NewEntryDialog.onPickAvatar = {
            pickEntryAvatar.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        com.memochat.app.util.Motion.run {
            binding.fabAdd.pressFeedback {
                NewEntryDialog.show(this@MainActivity) { entryId ->
                    com.memochat.app.ui.chat.ChatActivity.start(this@MainActivity, entryId)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRootBackground()
        maybeRemindQuickDraft()
    }

    private fun maybeRemindQuickDraft() {
        try {
            val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA).format(java.util.Date())
            if (Prefs.lastQuickRemind.toString() == today) return
            val arr = org.json.JSONArray(Prefs.quickWriteDrafts)
            if (arr.length() == 0) return
            val now = System.currentTimeMillis()
            var oldest: org.json.JSONObject? = null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val age = now - o.optLong("updatedAt")
                if (age >= 2 * 24 * 3600 * 1000L) {
                    if (oldest == null || o.optLong("updatedAt") < oldest.optLong("updatedAt")) oldest = o
                }
            }
            oldest ?: return
            val days = ((now - oldest.optLong("updatedAt")) / (24 * 3600 * 1000L)).toInt()
            Prefs.lastQuickRemind = today.toLong()
            android.app.AlertDialog.Builder(this)
                .setTitle("还有一句没写完")
                .setMessage("你有一句 ${days} 天前没写完的话，要继续吗？")
                .setPositiveButton("继续写") { _, _ ->
                    startActivity(android.content.Intent(this, com.memochat.app.ui.plaza.QuickWriteActivity::class.java)
                        .putExtra("draft_id", oldest.optString("id")))
                }
                .setNegativeButton("以后再说", null)
                .show()
        } catch (e: Exception) {}
    }

    private fun applyRootBackground() {
        val isNight = when (Prefs.mode) {
            Mode.LIGHT -> false
            Mode.DARK -> true
            else -> {
                val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        val appBg = com.memochat.app.util.Prefs.appBgPath
        if (appBg.isNotBlank() && java.io.File(appBg).exists()) {
            try {
                val bmp = android.graphics.BitmapFactory.decodeFile(appBg)
                // center-crop to screen size
                val dm = resources.displayMetrics
                val sw = dm.widthPixels.toFloat()
                val sh = dm.heightPixels.toFloat()
                val bw = bmp.width.toFloat()
                val bh = bmp.height.toFloat()
                val scale = maxOf(sw / bw, sh / bh)
                val nw = (bw * scale).toInt()
                val nh = (bh * scale).toInt()
                val sx = ((nw - sw) / 2f).toInt()
                val sy = ((nh - sh) / 2f).toInt()
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, nw, nh, true)
                val cropped = android.graphics.Bitmap.createBitmap(scaled, sx, sy, sw.toInt(), sh.toInt())
                val darkened = com.memochat.app.util.BgDarkener.darkenIfDarkMode(this, cropped)
                val d = android.graphics.drawable.BitmapDrawable(resources, darkened)
                window.setBackgroundDrawable(d)
                binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            } catch (e: Exception) {
                binding.root.setBackgroundResource(if (isNight) R.drawable.bg_main_night else R.drawable.bg_main)
            }
        } else {
            window.setBackgroundDrawableResource(
                if (isNight) R.drawable.bg_main_night else R.drawable.bg_main
            )
            binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    private fun setupBottomNav() {
        binding.navChat.setOnClickListener { switchTo(TAB_CHAT) }
        binding.navStats.setOnClickListener { switchTo(TAB_STAR) }
        binding.navStar.setOnClickListener { switchTo(TAB_STATS) }
        binding.navSettings.setOnClickListener { switchTo(TAB_SETTINGS) }
    }

    fun switchToFilter() = switchTo(TAB_FILTER)

    private fun switchTo(tab: String) {
        if (currentFragment != null && currentTab() == tab) return
        switchTabUi(tab)
        val f = when (tab) {
            TAB_STATS -> plazaFragment
            TAB_STAR -> starredFragment
            TAB_SETTINGS -> settingsFragment
            else -> chatFragment
        }
        showFragment(f, animated = true)
    }

    private fun currentTab(): String = when (currentFragment) {
        plazaFragment -> TAB_STATS
        starredFragment -> TAB_STAR
        settingsFragment -> TAB_SETTINGS
        else -> TAB_CHAT
    }

    private fun switchTabUi(tab: String) {
        val active = ContextCompat.getColor(this, R.color.wx_green)
        val inactive = 0xFF555555.toInt()
        binding.navChat.setColorFilter(if (tab == TAB_CHAT) active else inactive)
        binding.navStats.setColorFilter(if (tab == TAB_STAR) active else inactive)
        binding.navStar.setColorFilter(if (tab == TAB_STATS) active else inactive)
        binding.navSettings.setColorFilter(if (tab == TAB_SETTINGS) active else inactive)
        binding.bottomBar.setBackgroundResource(if (tab == TAB_SETTINGS) R.drawable.bg_glass_bar_solid else R.drawable.bg_glass_bar)
        binding.fabAdd.animate().cancel()
        if (tab == TAB_CHAT) {
            binding.fabAdd.visibility = android.view.View.VISIBLE
            binding.fabAdd.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220)
                .start()
        } else {
            binding.fabAdd.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(180)
                .withEndAction { binding.fabAdd.visibility = android.view.View.GONE }
                .start()
        }
    }

    private fun showFragment(fragment: Fragment, animated: Boolean) {
        val fm = supportFragmentManager
        val tx: FragmentTransaction = fm.beginTransaction()
        if (animated) {
            tx.setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
        }
        if (currentFragment != null) tx.hide(currentFragment!!)
        val tag = when (fragment) {
            plazaFragment -> "plaza"
            settingsFragment -> "settings"
            else -> "chat"
        }
        if (fragment.isAdded) {
            tx.show(fragment)
        } else {
            tx.add(R.id.fragment_container, fragment, tag)
        }
        tx.commit()
        currentFragment = fragment
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, currentTab())
    }

    companion object {
        private const val KEY_TAB = "tab"
        const val TAB_CHAT = "chat"
        const val TAB_FILTER = "filter"
        const val TAB_STATS = "plaza"
        const val TAB_STAR = "star"
        const val TAB_SETTINGS = "settings"
    }

    private var backPressedAt = 0L

    override fun onBackPressed() {
        if (chatFragment.isAdded && chatFragment.isSelectionMode()) {
            chatFragment.exitSelection()
            return
        }
        // if search text active on chat list, clear it first
        val et = chatFragment.view?.findViewById<android.widget.EditText>(com.memochat.app.R.id.search_input)
        if (et != null && et.text.isNotEmpty()) {
            et.setText("")
            chatFragment.reload()
            return
        }
        if (currentTab() != TAB_CHAT) {
            switchTo(TAB_CHAT)
            return
        }
        val now = System.currentTimeMillis()
        if (now - backPressedAt < 2000) {
            finish()
        } else {
            backPressedAt = now
            android.widget.Toast.makeText(this, "再次返回退出微记", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun seedTestData() {
        if (getSharedPreferences("onb", MODE_PRIVATE).getBoolean("seeded2", false)) return
        CoroutineScope(Dispatchers.IO).launch {
            val db = com.memochat.app.data.AppDatabase.get(this@MainActivity)
            val now = System.currentTimeMillis()
            val day = 86400000L
            val entries = listOf(
                com.memochat.app.data.Entry(title = "吐槽·地铁", date = "2026-09-15", weather = "晴", mood = "烦躁", createdAt = now - day*16, updatedAt = now - day*15, type = "吐槽"),
                com.memochat.app.data.Entry(title = "日记·搬家", date = "2026-09-17", weather = "阴", mood = "疲惫", createdAt = now - day*14, updatedAt = now - day*13, type = "日记"),
                com.memochat.app.data.Entry(title = "工作·需求评审", date = "2026-09-18", weather = "多云", mood = "平静", createdAt = now - day*13, updatedAt = now - day*12, type = "工作"),
                com.memochat.app.data.Entry(title = "吐槽·外卖", date = "2026-09-19", weather = "雨", mood = "生气", createdAt = now - day*12, updatedAt = now - day*11, type = "吐槽"),
                com.memochat.app.data.Entry(title = "日记·生日", date = "2026-09-20", weather = "晴", mood = "开心", createdAt = now - day*11, updatedAt = now - day*10, type = "日记"),
                com.memochat.app.data.Entry(title = "工作·上线", date = "2026-09-21", weather = "晴", mood = "兴奋", createdAt = now - day*10, updatedAt = now - day*9, type = "工作"),
                com.memochat.app.data.Entry(title = "吐槽·堵车", date = "2026-09-22", weather = "雾", mood = "烦躁", createdAt = now - day*9, updatedAt = now - day*8, type = "吐槽"),
                com.memochat.app.data.Entry(title = "日记·跑步", date = "2026-09-23", weather = "晴", mood = "开心", createdAt = now - day*8, updatedAt = now - day*7, type = "日记"),
                com.memochat.app.data.Entry(title = "工作·bug修复", date = "2026-09-24", weather = "阴", mood = "疲惫", createdAt = now - day*7, updatedAt = now - day*6, type = "工作"),
                com.memochat.app.data.Entry(title = "吐槽·空调", date = "2026-09-25", weather = "雨", mood = "烦躁", createdAt = now - day*6, updatedAt = now - day*5, type = "吐槽"),
                com.memochat.app.data.Entry(title = "日记·火锅", date = "2026-09-26", weather = "多云", mood = "开心", createdAt = now - day*5, updatedAt = now - day*4, type = "日记"),
                com.memochat.app.data.Entry(title = "工作·周报", date = "2026-09-27", weather = "晴", mood = "平静", createdAt = now - day*4, updatedAt = now - day*3, type = "工作"),
                com.memochat.app.data.Entry(title = "吐槽·快递", date = "2026-09-28", weather = "阴", mood = "生气", createdAt = now - day*3, updatedAt = now - day*2, type = "吐槽"),
                com.memochat.app.data.Entry(title = "日记·读书", date = "2026-09-29", weather = "晴", mood = "平静", createdAt = now - day*2, updatedAt = now - day*1, type = "日记"),
                com.memochat.app.data.Entry(title = "工作·新版本", date = "2026-09-30", weather = "多云", mood = "兴奋", createdAt = now - day*1, updatedAt = now, type = "工作"),
                com.memochat.app.data.Entry(title = "今日记录", date = "2026-10-01", weather = "晴", mood = "开心", createdAt = now, updatedAt = now, type = "日记"),
            )
            val ids = entries.map { db.entryDao().insert(it) }
            val samples = listOf(
                0L to "早高峰地铁被挤成相片，脚都离地了",
                0L to "车厢里有人外放短视频，真的服了",
                1L to "搬了一整天箱子，腰快断了",
                1L to "新家的阳台能看到夕阳，值了",
                2L to "产品又改需求，周一上午就开会",
                2L to "终于把评审意见过完了，松口气",
                3L to "外卖送错了地址，还不理赔",
                3L to "饿了一小时结果送来的是冷的",
                4L to "朋友陪我过了生日，蛋糕很好吃",
                4L to "许了个愿，希望今年顺利",
                5L to "新版本终于上线了，熬了两个通宵",
                5L to "监控看了一晚上，没出大问题",
                6L to "高架堵了四十分钟，迟到扣钱",
                7L to "晚上跑了五公里，比上周快了一分钟",
                8L to "那个诡异的 bug 终于定位到了",
                9L to "公司空调开得像冰窖，冻得发抖",
                10L to "和朋友吃了牛油火锅，辣得很爽",
                11L to "周报写了三小时，其实没干啥",
                12L to "快递又被放在驿站，还不通知",
                13L to "看完了一本小说，结局有点伤感",
                14L to "新版本灰度数据不错，留存涨了",
                15L to "今天心情不错，写点什么吧",
            )
            samples.forEach { (idx, text) ->
                val eid = ids[idx.toInt()]
                db.messageDao().insert(com.memochat.app.data.Message(entryId = eid, type = 0, content = text, createdAt = now - day*(idx%5)))
            }
            // Add one thread with 20 messages for testing
            val testEntry = com.memochat.app.data.Entry(title = "长对话测试", date = "2026-10-01", weather = "晴", mood = "平静", createdAt = now - 3600000L, updatedAt = now, type = "日记")
            val testId = db.entryDao().insert(testEntry)
            for (i in 1..50) {
                db.messageDao().insert(com.memochat.app.data.Message(entryId = testId, type = 0, content = "这是第 $i 条测试消息，用来验证长对话的滚动和气泡排版效果", createdAt = now - 3600000L + i*60000L))
            }
            getSharedPreferences("onb", MODE_PRIVATE).edit().putBoolean("seeded2", true).apply()
        }
    }
}
