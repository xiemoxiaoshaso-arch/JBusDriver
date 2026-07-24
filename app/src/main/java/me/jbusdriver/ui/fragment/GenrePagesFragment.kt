package me.jbusdriver.ui.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.util.ArrayMap
import android.view.View
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.arrayMapof
import me.jbusdriver.base.common.C
import me.jbusdriver.base.fromJson
import me.jbusdriver.base.ui.fragment.TabViewPagerFragment
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.GenrePageContract
import me.jbusdriver.mvp.GenrePageContract.GenrePagePresenter
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.presenter.GenrePagePresenterImpl
import me.jbusdriver.ui.data.enums.DataSourceType

/**
 * 类别分类
 */
class GenrePagesFragment : TabViewPagerFragment<GenrePagePresenter, GenrePageContract.GenrePageView>(),
    GenrePageContract.GenrePageView {

    override val titleValues: MutableList<String> = mutableListOf()

    override val fragmentValues: MutableList<List<Genre>> = mutableListOf()

    private val fragmentsBak = mutableListOf<Fragment>()


    override fun createPresenter() =
        GenrePagePresenterImpl(arguments?.getString(C.BundleKey.Key_1) ?: error("no url for GenrePagesFragment"))

    override val mTitles: List<String>
        get() = titleValues

    override val mFragments: List<Fragment>
        get() = fragmentsBak

    override fun initWidget(rootView: View) {
        //请求数据完成后再加载
        //super.initWidget(rootView)
    }

    override fun <T> showContent(data: T?) {
        require(titleValues.size == fragmentValues.size)
        fragmentValues.mapTo(fragmentsBak) {
            GenreListFragment.newInstance(it)
        }
        initForViewPager()
    }

    companion object {

        // 🌟 核心修正：补全欧美类别域名判定与动态路径拼接
        fun newInstance(type: DataSourceType) = GenrePagesFragment().apply {
            val urls =
                CacheLoader.acache.getAsString(C.Cache.BUS_URLS)?.let { GSON.fromJson<ArrayMap<String, String>>(it) }
                    ?: arrayMapof()
            
            // 1. 判断基础域名：如果是欧美类别，使用 defaultXyzUrl 独立域名，否则使用 defaultFastUrl 主站域名
            val defaultHost = if (type == DataSourceType.XYZ_GENRE) JAVBusService.defaultXyzUrl else JAVBusService.defaultFastUrl
            
            // 2. 动态拼接路径，使用对应域名加上 DataSourceType 中定义好的 type.key (如 "xyz/genre")
            val url = urls[type.key] ?: (defaultHost.trimEnd('/') + "/" + type.key)
            
            // 🌟 加上这一行高亮红色日志：
            android.util.Log.e("JBUS_DEBUG_MENU", "【欧美类别入口】最终拼装出的请求 URL: $url")

            arguments = Bundle().apply {
                putString(C.BundleKey.Key_1, url)
            }
        }
    }


}
