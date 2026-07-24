package me.jbusdriver.mvp.presenter

import android.support.v4.util.ArrayMap
import io.reactivex.Flowable
import me.jbusdriver.base.*
import me.jbusdriver.base.common.C
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.model.AbstractBaseModel
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.common.bean.ILink
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.PageLink
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.mvp.bean.newPageMovie
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 首页列表
 */
open class HomeMovieListPresenterImpl(val type: DataSourceType, val link: ILink) : LinkAbsPresenterImpl<Movie>(link) {

    private val urls by lazy {
        CacheLoader.acache.getAsString(C.Cache.BUS_URLS)?.let { GSON.fromJson<ArrayMap<String, String>>(it) }
            ?: arrayMapof()
    }
    private val saveKey: String
        inline get() = "${type.key}$IsAll"
    // 🌟 核心修正 1：实例化 Retrofit 服务时，欧美区必须使用 defaultXyzUrl 独立域名
    private val service by lazy {
        val defaultHost = if (type == DataSourceType.XYZ) JAVBusService.defaultXyzUrl else JAVBusService.defaultFastUrl
        JAVBusService.getInstance(
            urls[type.key] ?: defaultHost
        ).apply { JAVBusService.INSTANCE = this }
    }

    // 🌟 核心修正 1：实例化 Retrofit 服务时，欧美区必须使用 defaultXyzUrl 独立域名
    private val service by lazy {
        val defaultHost = if (type == DataSourceType.XYZ) JAVBusService.defaultXyzUrl else JAVBusService.defaultFastUrl
        JAVBusService.getInstance(
            urls[type.key] ?: defaultHost
        ).apply { JAVBusService.INSTANCE = this }
    }

    // 🌟 核心修正 2：在构建网络请求 urlN 时，欧美区必须使用 defaultXyzUrl 独立域名作为基础进行拼接
    private val loadFromNet = { page: Int ->
        val defaultHost = if (type == DataSourceType.XYZ) JAVBusService.defaultXyzUrl else JAVBusService.defaultFastUrl
        val urlN = urls.getOrElse(type.key) { defaultHost }.let { url ->
            return@let if (page == 1) url else "$url${type.prefix}$page"
        }
        // 🌟 核心调试：在这里加入最高级别的红色高亮日志打印
        android.util.Log.e("JBUS_DEBUG", "==================================================")
        android.util.Log.e("JBUS_DEBUG", "【请求触发】当前点击板块名称: ${type.key}")
        android.util.Log.e("JBUS_DEBUG", "【请求触发】从缓存 urls[${type.key}] 中获取的值: ${urls[type.key]}")
        android.util.Log.e("JBUS_DEBUG", "【请求触发】当前的备用域名 defaultHost: $defaultHost")
        android.util.Log.e("JBUS_DEBUG", "【请求触发】最终拼装发出的网络请求 urlN: $urlN")
        android.util.Log.e("JBUS_DEBUG", "==================================================")
        KLog.d("loadFromNet $urlN")
        //existmag=all
        //add his
        val pageLink = PageLink(page = page, title = type.key, link = urlN)
        addHistory(pageLink)
        service.get(urlN, if (IsAll) "all" else "").addUserCase().doOnNext {
            if (page == 1 && !it.isNullOrBlank()) CacheLoader.lru.put(saveKey, it!!)
        }.map { Jsoup.parse(it) }.doOnError {
            //可能网址被封
            CacheLoader.acache.remove(C.Cache.BUS_URLS)
        }
    }


    override val model: BaseModel<Int, Document> = object : AbstractBaseModel<Int, Document>(loadFromNet) {
        override fun requestFromCache(t: Int): Flowable<Document> =
            Flowable.concat(
                CacheLoader.justLru(saveKey).map { Jsoup.parse(it) },
                requestFor(t)
            ).firstOrError().toFlowable()
    }


    override fun stringMap(page: PageInfo, str: Document) = loadMovieFromDoc(str).let {
        when (mView?.pageMode) {
            AppConfiguration.PageMode.Page -> {
                listOf(newPageMovie(page.activePage, page.referPages)) + it
            }
            else -> it
        }
    }


    override fun onRefresh() {
        CacheLoader.removeCacheLike(saveKey, isRegex = false)
        super.onRefresh()
    }

}
