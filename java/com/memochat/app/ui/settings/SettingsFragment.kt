package com.memochat.app.ui.settings

import android.content.Intent
import android.provider.MediaStore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.databinding.FragmentSettingsBinding
import com.memochat.app.util.BitmapUtil
import com.memochat.app.util.Mode
import com.memochat.app.util.Prefs
import com.memochat.app.util.ThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val pickAvatar =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val path = BitmapUtil.copyToPrivate(requireContext(), uri, "avatar")
                if (path != null) {
                    Prefs.avatarPath = path
                    reloadAvatar()
                    Toast.makeText(requireContext(), "头像已更新", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private var pendingCropType = 0 // 0=chat,1=app
    private val pickBg =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pendingCropType = 0
                launchCrop(uri)
            }
        }

    private val pickAppBg =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pendingCropType = 1
                launchCrop(uri)
            }
        }

    private fun dp_(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun AlertDialog.glass() {
        setOnShowListener {
            window?.setBackgroundDrawableResource(R.drawable.bg_glass_dialog)
            window?.setWindowAnimations(R.style.DialogGlassAnim)
        }
    }

    private var pendingCropPath: String? = null
    private var pendingCropUri: android.net.Uri? = null

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val out = pendingCropPath ?: return@registerForActivityResult
            if (pendingCropType == 0) Prefs.chatBgPath = out
            else Prefs.appBgPath = out
            android.widget.Toast.makeText(requireContext(), "背景已更新", android.widget.Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressed()
        }
    }

    private fun launchCrop(uri: android.net.Uri) {
        try {
            val dir = java.io.File(requireContext().filesDir, "media").apply { mkdirs() }
            val out = java.io.File(dir, if (pendingCropType == 0) "chatbg_" else "appbg_" + System.currentTimeMillis() + ".jpg")
            pendingCropPath = out.absolutePath
            val i = android.content.Intent(requireContext(), com.memochat.app.ui.crop.CropActivity::class.java)
            i.putExtra(com.memochat.app.ui.crop.CropActivity.EXTRA_URI, uri)
            i.putExtra(com.memochat.app.ui.crop.CropActivity.EXTRA_OUT, out.absolutePath)
            cropLauncher.launch(i)
        } catch (t: Throwable) {
            android.widget.Toast.makeText(requireContext(), "启动失败: ${t.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reloadAvatar()
        updateModeUi()
        binding.bgSummary.text =
            if (Prefs.chatBgPath.isNotEmpty()) getString(R.string.settings_bg_custom)
            else getString(R.string.settings_bg_default)

        binding.rowAvatar.setOnClickListener {
            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.rowModeSystem.setOnClickListener { setMode(Mode.SYSTEM) }
        binding.rowModeLight.setOnClickListener { setMode(Mode.LIGHT) }
        binding.rowModeDark.setOnClickListener { setMode(Mode.DARK) }

        binding.switchEnterSend.isChecked = Prefs.enterSend
        binding.switchEnterSend.setOnCheckedChangeListener { _, v ->
            Prefs.enterSend = v
        }

        binding.rowBg.setOnClickListener {
            val options = arrayOf(
                getString(R.string.settings_bg_default),
                getString(R.string.settings_bg_custom)
            )
            val d = AlertDialog.Builder(requireContext(), R.style.Theme_MemoChat_Dialog)
                .setTitle(R.string.settings_bg)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            Prefs.chatBgPath = ""
                            binding.bgSummary.text = getString(R.string.settings_bg_default)
                            requireActivity().onBackPressed()
                        }
                        1 -> pickBg.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }.create()
            d.glass()
            d.show()
        }

        binding.appBgSummary.text = if (Prefs.appBgPath.isNotBlank()) "已自定义" else "默认"
        binding.rowAppBg.setOnClickListener {
            val options = arrayOf("默认", "选择图片")
            val d = AlertDialog.Builder(requireContext(), R.style.Theme_MemoChat_Dialog)
                .setTitle("主题背景")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            Prefs.appBgPath = ""
                            binding.appBgSummary.text = "默认"
                            requireActivity().onBackPressed()
                        }
                        1 -> pickAppBg.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }.create()
            d.glass()
            d.show()
        }

        binding.rowTrash.setOnClickListener {
            startActivity(Intent(requireContext(), com.memochat.app.ui.trash.TrashActivity::class.java))
        }
        binding.rowVersion.setOnLongClickListener {
            startActivity(Intent(requireContext(), com.memochat.app.ui.trash.TrashActivity::class.java))
            true
        }
        binding.rowVersion.setOnClickListener {
            val v = try {
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            } catch (e: Exception) { "?" }
            Toast.makeText(requireContext(), "当前已是最新版本 v$v", Toast.LENGTH_SHORT).show()
        }

        binding.rowUpdateAd.setOnClickListener {
            val scroll = android.widget.ScrollView(requireContext())
            val tv = android.widget.TextView(requireContext())
            tv.textSize = 14f
            tv.setPadding(48, 32, 48, 32)
            tv.setTextColor(0xFF222222.toInt())
            scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            tv.text = """v2.7.0
【升级】
· 聊天气泡彻底重写：纯 shape 圆角、浅绿 #95EC69、宽度自适应内容，最长不超过屏幕 75%
· 长按消息菜单锚点改为气泡本身，紧贴气泡上方 8dp，靠顶时自动翻到下方
· 回到底部圆形按钮重做：白底圆 + 向下箭头，上翻旧消息时出现，50ms 快速滚回最新
· 输入栏改为不透明白底 + 柔和阴影，固定在底部不随列表滚动
· 新建条目弹窗顶部大圆角，窗口背景透明露出圆角
· 测试条目增加到 16 条，方便预览各种界面效果

【修复】
· 长消息文字不再溢出气泡
· 聊天页默认滚动条把手去掉
· 回到底部按钮长记录瞬间到位、短记录快速平滑

v2.6.2
【升级】
· 聊天气泡组件重写：浅绿底、小圆角、宽度自适应，最大不超过屏幕 75%
· 长按消息菜单默认贴在气泡上方，气泡靠顶时自动翻到下方
· 聊天页右下角新增回到底部圆形按钮，上翻旧消息时出现，点击快速滚回最新
· 输入栏改为不透明白底 + 柔和阴影，消息不再从底下透出来
· 新建条目弹窗顶部大圆角，和 App 其他弹窗风格统一

【修复】
· 长消息文字排版不再溢出气泡
· 回到底部按钮滚动速度优化，长记录瞬间到位、短记录快速平滑
· 聊天页默认滚动条把手去掉

v2.6.1
【升级】
· 条目列表多选删除界面重做：顶部系统风格选择栏（返回/已选 X 项/全选）
· 底部删除按钮改为悬浮液态玻璃胶囊，居中在导航栏上方
· 单条右滑删除和批量删除统一走回收站逻辑，30 天内可恢复

【修复】
· 批量删除提示文案改为"已移入回收站，30 天内可恢复"
· 顶部选择栏文字深色模式下适配

v2.6.0
【升级】
· 补记日期选择器深色模式全面适配：普通日期浅白、未来日期深灰、星期行浅灰
· 年/月滚轮打开时回显当前已选值，不再每次跳回默认
· 滚轮选中项计算改为离中心最近的那一个，选完日历真正跳转
· 补记提示条和"回到今日"按钮移到输入栏正上方，做成细胶囊
· 浅色模式提示条半透明白底深灰字，深色模式深灰半透明底
· "修改标题"弹窗重做：不透明白/深灰底，圆角输入框，按钮并排
· 深色模式下补记日历顶部"月"字颜色适配

【修复】
· 滚轮选完年月和实际日历对不上
· 深色模式下日历日期看不清
· 补记提示条和顶部标题重叠
· 更新公告标题深色下看不清

v2.5.0
【升级】
· 全 App 深色模式重做：背景 #1A1A1A，卡片 #2C2C2C，文字层次更柔和
· 自定义壁纸在深色模式下自动压暗，上面的字看得清
· 聊天气泡绿色调暗，深色下不刺眼
· 聊天页图标、广场卡片、回忆抽卡、今日一问、时光信箱、我的数据、写点什么全部适配深色
· 星标页右上角搜索按钮移除
· "发现更多记录自己的方式"加了半透明胶囊底，任何背景都看得清
· 更新公告标题居中显示

【修复】
· 深色模式下更新公告标题看不清
· 深色模式下补记日历、年/月滚轮弹窗白底突兀
· 深色模式下广场功能卡片仍是白底
· 深色模式下聊天页输入栏图标仍是白色
· 我的数据页卡片未跟随深色主题
· 写点什么页面标题和输入文字深色下看不清

v2.4.0
【升级】
· 补记日期选择器重做为底部弹窗，左右滑连续翻月
· 年份和月份改成滚轮选择器，滚动时中间项自动放大变粗变黑
· 日历7列对齐星期，未来日期灰掉不可选，今天浅绿圈
· 日历切换月带平滑滑动动画

v2.3.0
【新增】
· 广场页：底部导航新增入口，集中展示记录玩法
· 回忆抽卡：随机翻一条你以前写过的话
· 时光信箱：写给未来的自己，到日子自动提醒
· 今日一问：每天一个小问题，帮你开头
· 我的数据：连续天数、总条数、累计字数、写作节奏、最常说的词、条目排行

【升级】
· 广场图标统一为细线条描边风格，外套浅灰圆角底
· 广场与我的星标顶部标题栏改为胶囊样式，与条目列表一致
· 我的数据页改为白底圆角卡片，主题背景透出
· 时间切换胶囊：全部 / 近7天 / 近30天，选中变绿

v2.2.0
【新增】
· 经典小黄脸表情面板，不再显示系统emoji
· 输入框和聊天记录里的表情直接显示为图片
· 表情面板"最近使用"区，记住常用表情
· 草稿功能：没发完的内容自动保存，列表红色标注

【升级】
· 输入框旁删除键一次删整个表情
· 表情面板底部留白，最后一行不被按钮挡住

v2.1.1
【修复】
· 深色模式下星标页面样式错乱
· 星标页面标题乱码
· 条目列表深色模式下置顶/删除按钮透出

v2.1.0
【新增】
· 图片可全屏查看，支持双指放大缩小、左右翻看

【升级】
· 不同比例的图片自动居中显示

v2.0.0
【新增】
· 底部导航"星标"入口，收藏的消息一键查看
· 点击收藏消息自动跳到聊天对应位置，高亮提示
· 长按消息弹出操作菜单（复制/收藏/编辑/撤回等）
· 上翻旧消息时右下角出现回到底部按钮

v1.7.0
【新增】
· 条目列表右滑可置顶或删除
· 删除前二次确认，删错了能从回收站找回
· 输入栏旁日历图标可补记旧消息

v1.6.0
【新增】
· 备份功能：导出文字版日记、导出完整备份包
· 支持从备份包恢复全部数据

v1.5.0
【新增】
· 回收站：删除的内容30天内可恢复
· 删除时提示"已移到回收站"，不再一删就没

v1.4.0
【升级】
· 统计页重做：记录条数、记录天数、连续记录天数
· 心情变化折线图，可按时间区间筛选"""
            scroll.addView(tv)
            val titleTv = android.widget.TextView(requireContext()).apply {
                text = "更新公告"
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                val dm = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                setTextColor(if (dm == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
                setPadding(0, dp_(20), 0, dp_(10))
            }
            val wrap = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(titleTv)
                addView(scroll)
            }
            val d = AlertDialog.Builder(requireContext(), R.style.Theme_MemoChat_Dialog)
                .setView(wrap)
                .setPositiveButton("知道了", null)
                .create()
            d.glass()
            d.show()
        }
        try {
            val pm = requireContext().packageManager
            val info = pm.getPackageInfo(requireContext().packageName, 0)
            binding.versionSummary.text = info.versionName
            binding.updateAdSummary.text = "v" + info.versionName + "，点击查看更新详情"
        } catch (e: Exception) {
            binding.versionSummary.text = "未知"
        }

        binding.rowExport.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.memochat.app.ui.backup.BackupActivity::class.java))
        }
    }

    private fun setMode(mode: Int) {
        Prefs.mode = mode
        ThemeHelper.apply(mode)
        updateModeUi()
    }

    private fun updateModeUi() {
        val m = Prefs.mode
        binding.checkSystem.isVisible(m == Mode.SYSTEM)
        binding.checkLight.isVisible(m == Mode.LIGHT)
        binding.checkDark.isVisible(m == Mode.DARK)
    }

    private fun reloadAvatar() {
        val size = (56 * resources.displayMetrics.density).toInt()
        BitmapUtil.loadAvatarRound(binding.avatar, Prefs.avatarPath, size)
    }

    private fun android.view.View.isVisible(v: Boolean) {
        visibility = if (v) View.VISIBLE else View.INVISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
