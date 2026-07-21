package me.jbusdriver.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.nav_header_main.view.*
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.AppBaseActivity
import me.jbusdriver.common.JBus
import me.jbusdriver.mvp.MainContract
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.presenter.MainPresenterImpl
import java.util.concurrent.TimeUnit

class MainActivity : AppBaseActivity<MainContract.MainPresenter, MainContract.MainView>(),
    NavigationView.OnNavigationItemSelectedListener, MainContract.MainView {

    private val navigationView by lazy { findViewById<NavigationView>(R.id.nav_view) }
    private var selectMenu: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) intent.putExtras(savedInstanceState)

        // 👈 【核心修改 1】：App 启动时，自动读取本地保存的自定义源站域名，直接生效
        val sp = getSharedPreferences("domain_config", Context.MODE_PRIVATE)
        val savedUrl = sp.getString("custom_javbus_url", "")
        if (!savedUrl.isNullOrEmpty()) {
            me.jbusdriver.http.JAVBusService.defaultFastUrl = savedUrl
            me.jbusdriver.http.JAVBusService.INSTANCE = me.jbusdriver.http.JAVBusService.getInstance(savedUrl)
        }

        bindRx()
        initNavigationView()
        initFragments()
    }

    private fun bindRx() {
        RxBus.toFlowable(MenuChangeEvent::class.java)
            .delay(100, TimeUnit.MILLISECONDS) //稍微延迟,否则设置可能没有完成
            .compose(SchedulersCompat.computation())
            .subscribeBy {
                val mayAdded = MenuOp.Ops.map { it.id.toString() }
                supportFragmentManager.fragments.filter { it.tag in mayAdded }.forEach {
                    supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
                }

                initFragments()
            }
            .addTo(rxManager)

        RxBus.toFlowable(CategoryChangeEvent::class.java)
            .debounce(500, TimeUnit.MILLISECONDS) //稍微延迟,否则设置可能没有完成
            .compose(SchedulersCompat.computation())
            .subscribeBy {
                supportFragmentManager.findFragmentByTag(R.id.mine_collect.toString())?.let {
                    supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
                }
                if (selectMenu?.itemId == R.id.mine_collect) setNavSelected()

            }.addTo(rxManager)

    }


    private fun initNavigationView() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()


        navigationView?.getHeaderView(0)?.apply {
            tv_app_version.text = packageInfo?.versionName ?: "未知版本"
            ll_git_url.setOnClickListener {
                browse("https://github.com/Ccixyj/JBusDriver")
            }
            ll_telegram.setOnClickListener {
                browse("https://t.me/joinchat/HBJbEA-ka9TcWzaxjmD4hw")
            }
            
            // 👈 【核心修改 2】：将原先“加载出现问题尝试点击”的点击事件，重定向到我们的源站设置对话框中
            ll_click_reload.setOnClickListener {
                showDomainSettingDialog()
            }

            tv_app_setting.setOnClickListener {
                SettingActivity.start(this@MainActivity)
                drawer.closeDrawer(GravityCompat.START)
            }


            fun tintTextLeftDrawable(parent: ViewGroup) {

                (0..parent.childCount).forEachIndexed { i, _ ->
                    //如果是容器,直接查子view
                    (parent.getChildAt(i) as? ViewGroup)?.let {
                        Schedulers.trampoline().scheduleDirect {
                            tintTextLeftDrawable(it)
                        }

                    } ?: (parent.getChildAt(i) as? TextView)?.compoundDrawables?.forEach {
                        if (it != null)
                            DrawableCompat.setTint(it, R.color.colorAccent.toColorInt())
                    }
                }
            }
            if (Build.VERSION.SDK_INT < 23 && this as? ViewGroup != null) {
                Schedulers.single().scheduleDirect {
                    Schedulers.trampoline().scheduleDirect {
                        tintTextLeftDrawable(this)
                    }.addTo(rxManager)
                }
            }


        }
        navigationView.setNavigationItemSelectedListener(this)

    }


    private fun initFragments() {

        MenuOp.Ops.forEach {
            navigationView.menu.findItem(it.id).isVisible = it.isHow
        }
        setNavSelected()
    }

    private fun setNavSelected() {
        val id = (MenuOp.Ops - MenuOp.mine).find { it.isHow }?.id
            ?: MenuOp.Ops.find { it.isHow }?.id ?: let {
                toast("至少配置一项菜单!!!!")
                return
            }
        val menuId = intent.getIntExtra("MenuSelectedItemId", id)
        val select = navigationView.menu.findItem(menuId)
        select?.let {
            navigationView.setCheckedItem(it.itemId)
            onNavigationItemSelected(it)
        }

    }


    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        switchFragment(item.itemId)
        //更新当前选择菜单
        selectMenu = item
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        supportActionBar?.title = selectMenu?.title
        return true
    }

    private fun switchFragment(itemId: Int) {
        val ft = supportFragmentManager.beginTransaction()

        val replace = supportFragmentManager.findFragmentByTag(itemId.toString()) ?: let {
            MenuOp.Ops.find { it.id == itemId }?.initializer?.invoke()?.apply {
                ft.add(R.id.content_main, this, itemId.toString())
            } ?: error("no matched fragment")
        }
        //如果id 与 selectMenu的id不一致则隐藏前一个选择菜单
        if (itemId != selectMenu?.itemId) {
            supportFragmentManager.findFragmentByTag(selectMenu?.itemId.toString())?.let {
                ft.hide(it)
            }
        }
        ft.show(replace)
        ft.commitAllowingStateLoss()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        selectMenu?.let {
            outState?.putInt("MenuSelectedItemId", it.itemId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun createPresenter() = MainPresenterImpl()

    override val layoutId = R.layout.activity_main


    @SuppressLint("ResourceAsColor")
    override fun <T> showContent(data: T?) {
        /*
        if (data is UpdateBean) {
            val bean = data as UpdateBean
            if (viewContext.packageInfo?.versionCode ?: -1 < bean.versionCode) {
                MaterialDialog.Builder(this).title("更新(${bean.versionName})")
                    .content(bean.desc)
                    .neutralText("下次更新")
                    .neutralColor(R.color.secondText)
                    .positiveText("更新")
                    .onPositive { _, _ ->
                        browse(bean.url)
                    }
                    .dismissListener {
                        showNotice(data)
                    }
                    .show()
            }
        }
        */
        if (data is NoticeBean) {
            showNotice(data)
        }
        
    }

    @SuppressLint("ResourceAsColor")
    private fun showNotice(notice: Any?) {
        val shared by lazy { getSharedPreferences("config", Context.MODE_PRIVATE) }
        if (notice != null && notice is NoticeBean && !TextUtils.isEmpty(notice.content) && notice.id > 0 && shared.getInt(
                NoticeIgnoreID,
                -1
            ) < notice.id
        ) {
            MaterialDialog.Builder(this).title("公告")
                .content(notice.content!!)
                .neutralText("忽略该提示")
                .neutralColor(R.color.secondText)
                .onNeutral { _, _ ->
                    shared.edit().putInt(NoticeIgnoreID, notice.id).apply()
                }
                .positiveText("知道了")
                .show()
        }
    }

    // 👈 【核心修改 3】：加入弹窗的实现业务逻辑，使用 Android Support 的 AlertDialog 以防报错
    private fun showDomainSettingDialog() {
        val context: Context = this
        val sp = context.getSharedPreferences("domain_config", Context.MODE_PRIVATE)
        
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_change_domain, null)
        val spinner = view.findViewById<Spinner>(R.id.spinner_domains)
        val editText = view.findViewById<EditText>(R.id.et_custom_domain)

        // 1. 【动态读取】从本地缓存中读取 Gitee 采集上来的最新镜像列表
        val backupStr = sp.getString("backup_domains", "")
        val domainList = if (backupStr.isNullOrEmpty()) {
            // 💡 兜底机制：万一网络不好或者首次打开还没缓存成功，才用这三个作为保底展示
            listOf(
                "https://www.busjav.bond",
                "https://www.fanbus.cyou",
                "https://www.busjav.cyou"
            )
        } else {
            // 将缓存的逗号分隔字符串，动态还原解析为域名列表 List
            backupStr.split(",")
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, domainList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // 回显当前正在使用的域名到输入框
        val currentUrl = sp.getString("custom_javbus_url", me.jbusdriver.http.JAVBusService.defaultFastUrl)
        editText.setText(currentUrl)

        // 展示对话框（使用 support.v7 库的 AlertDialog 兼容包，防止编译报错）
        android.support.v7.app.AlertDialog.Builder(context)
            .setTitle("源站域名设置")
            .setView(view)
            .setPositiveButton("保存并应用") { dialog, _ ->
                val inputUrl = editText.text.toString().trim()
                val targetUrl = if (inputUrl.isNotEmpty()) inputUrl else spinner.selectedItem.toString()

                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    sp.edit().putString("custom_javbus_url", targetUrl).apply()
                    
                    me.jbusdriver.http.JAVBusService.defaultFastUrl = targetUrl
                    me.jbusdriver.http.JAVBusService.INSTANCE = me.jbusdriver.http.JAVBusService.getInstance(targetUrl)
                    
                    Toast.makeText(context, "源站设置成功！已切换至：$targetUrl", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "格式错误：域名必须以 http(s):// 开头", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    companion object {
        const val NoticeIgnoreID = "notice_ignore_id"
        fun start(current: Activity) {
            current.startActivity(Intent(current, MainActivity::class.java))
        }
    }
}
