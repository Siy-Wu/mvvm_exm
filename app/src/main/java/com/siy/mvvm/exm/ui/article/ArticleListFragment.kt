package com.siy.mvvm.exm.ui.article

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.findNavController
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.siy.mvvm.exm.R
import com.siy.mvvm.exm.base.Injectable
import com.siy.mvvm.exm.base.MvvmDb
import com.siy.mvvm.exm.base.glide.GlideApp
import com.siy.mvvm.exm.base.repository.flowNetworkBoundResource
import com.siy.mvvm.exm.base.repository.loadFlowDataByPage
import com.siy.mvvm.exm.base.ui.BaseFragment
import com.siy.mvvm.exm.base.ui.navigateAnimate
import com.siy.mvvm.exm.databinding.FragmentArticleListBinding
import com.siy.mvvm.exm.db.dao.ArticleDao
import com.siy.mvvm.exm.db.dao.BannerDao
import com.siy.mvvm.exm.http.*
import com.siy.mvvm.exm.ui.Article
import com.siy.mvvm.exm.ui.Banner
import com.siy.mvvm.exm.utils.dip2px
import com.siy.mvvm.exm.utils.inflater
import com.siy.mvvm.exm.utils.throttleFist
import com.siy.mvvm.exm.views.header.CommonHeader
import com.siy.mvvm.exm.views.loopingviewpager.LoopViewPager
import com.siy.mvvm.exm.views.search.AutoSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import ru.ldralighieri.corbind.view.clicks
import javax.inject.Inject
import javax.inject.Singleton


/**
 *
 * Created by Siy on 2019/10/10.
 *
 * 关于Navigation切换界面调用onDestroyView()和onCreateView()状态保留问题
 * Ian Lake：
 * 您不必每次调用onCreateView时都为新视图inflater-您可以保留对您第一次创建的View的引用，然后再次返回它。当然，对于不可见的内容，这会不断浪费内存和资源。保持数据>>您的视图
 *
 *  关于保留视图的引用，内存泄露问题：
 *  Ian Lake：
 *  确保您没有将setRetainInstance(true)与带有Views的Fragments一起使用，或者不在ViewModel中存储任何引用context的Views和things
 *  由于视图引用了旧的上下文，因此视图将永远无法幸免于configuration更改驱动的Activity 重启。
 *
 *  Ian Lake Tips:
 *  请记住，即使不缓存视图本身，Fragment视图也会自动保存和恢复其状态。如果不是这种情况，则应首先解决该问题（确保视图具有android：id等）。否则，保留片段中的视图不是泄漏。
 *
 *
 * @see <a href="https://issuetracker.google.com/issues/109856764">Issue Tracker -  Transaction type is not available with Navigation Architecture Component</a>
 * @see <a href="https://issuetracker.google.com/issues/127932815">Issue Tracker -   Open fragment without lose the previous fragment states</a>
 * @see <a href="https://github.com/android/architecture-components-samples/issues/530">github -  architecture-components-samples</a>
 * @see <a href="http://twitter.com/ianhlake/status/1103522856535638016">twitter - Ian Lake(Navigation)</a>
 *
 * @author Siy
 */
class ArticleListFragment(override val layoutId: Int = R.layout.fragment_article_list) :
    BaseFragment<FragmentArticleListBinding>(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val mViewModel: ArticleListViewModel by viewModels {
        viewModelFactory
    }

    override fun initViewsAndEvents(view: View) {
        val headerView = LoopViewPager(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dip2px(200f))
            adapter = BannerAdapter(viewLifecycleOwner.lifecycleScope)
        }

        val artAdapter = ArticleListAdapter(null).apply {
            addHeaderView(headerView)
            headerLayout?.id = R.id.rv_header_id
            setHeaderAndEmpty(true)
        }

        mViewDataBinding.run {
            viewModel = mViewModel
            header = object : CommonHeader() {
                init {
                    title.value = "首页"
                    showTitleIcon.value = false
                }

                override fun onBackClick() {
                    navController.popBackStack()
                }
            }

            search = object : AutoSearch(viewLifecycleOwner, mViewModel.searchStr.value) {
                override fun searchApi(searchStr: String) {
                    mViewModel.showArctiles(searchStr)
                }
            }

            click0s = mapOf(
                "onRefresh" to mViewModel::refresh,
                "onLoadMore" to mViewModel::loadMore
            )

            onItemClick = fun(adapter: BaseQuickAdapter<Any, BaseViewHolder>, pos: Int) {
                (adapter.getItem(pos) as? Article)?.run {
                    navController.navigateAnimate(
                        ArticleListFragmentDirections.actionFirstPageFragmentToWebViewFragment(
                            link
                        )
                    )
                }
            }
            adapter = artAdapter
        }
        setUpObserver(headerView)
    }

    private fun setUpObserver(headerView: LoopViewPager) {
        mViewModel.banners.observe(viewLifecycleOwner) {
            if (it.data?.isNotEmpty() == true) {
                headerView.adapter = BannerAdapter(viewLifecycleOwner.lifecycleScope, it.data)
            }
        }
    }

    class BannerAdapter(
        private val scope: CoroutineScope,
        private var banners: List<Banner>? = null
    ) : PagerAdapter() {

        override fun isViewFromObject(view: View, `object`: Any) = view == `object`

        override fun getCount() = banners?.size ?: 0

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = container.inflater.inflate(R.layout.fleet_free_banner_view, container, false)
            val view1 = view.findViewById<ImageView>(R.id.imageview)
            view1.scaleType = ImageView.ScaleType.CENTER_CROP
            view1.setBackgroundResource(R.drawable.common_shape_banner_bg)

            val item = banners?.get(position)
            item?.let { banner ->
                view1.clicks()
                    .throttleFist(1000)
                    .onEach {
                        view1.findNavController().navigateAnimate(
                            ArticleListFragmentDirections.actionFirstPageFragmentToWebViewFragment(
                                banner.url
                            )
                        )
                    }.launchIn(scope)

                GlideApp.with(container.context)
                    .load(banner.imagePath)
                    .transition(DrawableTransitionOptions.withCrossFade(600))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .into(view1)
            }
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun getItemPosition(`object`: Any): Int {
            val index = banners?.indexOf(`object`) ?: -1
            return if (index == -1) {
                POSITION_NONE
            } else {
                index
            }
        }
    }
}

class ArticleListViewModel @Inject constructor(
    rep: ArticleListRep
) : ViewModel() {
    val banners = MutableLiveData<Resource<List<Banner>?>>()

    /**
     * 搜索的关键字
     */
    private val _searchStr = ConflatedBroadcastChannel<String>()
    val searchStr: LiveData<String>
        get() = MutableLiveData<String>()//_searchStr.asFlow().asLiveData()

    private val articleResult = _searchStr.asFlow().map {
        Log.e("debug", "-------------------------------------------")
        rep.getArtclesByPage(it)
    }


    /**
     * 列表
     */
    val articleList = MutableLiveData<List<Article>>()


    /**
     * 加载状态
     */
    val loadState = MutableLiveData<PAGESTATUS>()


    /**
     * 刷新状态
     */
    val refreshState = MutableLiveData<Boolean>()


    /**
     * 加载更多方法
     */
    fun loadMore() {
        loadMore?.invoke()

    }

    var loadMore: (() -> Unit)? = null
    var refresh: (() -> Unit)? = null

    /**
     * 刷新方法
     */
    fun refresh() {
        refresh?.invoke()
    }


    init {
        viewModelScope.launch {
            supervisorScope {

                launch {
                    rep.getBanners().collect {
                        banners.value = it
                    }
                }

                launch {
                    articleResult.collect { pageing ->
                        refresh = pageing.refresh
                        loadMore = pageing.loadData
                        list(pageing.list)
                        loadState(pageing.loadStatus)
                        refreshState(pageing.refreshStatus)
                    }
                }
            }

        }
    }

    private fun list(listFlow: Flow<List<Article>?>) {
        viewModelScope.launch {
            listFlow.collect {
                articleList.value = it
            }
        }
    }

    private fun loadState(load: Flow<PageRes>) {
        viewModelScope.launch {
            load.collect {
                loadState.value = it.status
            }
        }
    }

    private fun refreshState(refresh: Flow<PageRes>) {
        viewModelScope.launch {
            refresh.collect {
                refreshState.value = it.status == PAGESTATUS.LOADING
            }
        }
    }

    fun showArctiles(str: String): Boolean {
        val alreadyValue = _searchStr.valueOrNull
        if (str == alreadyValue) {
            return false
        }
        if (!_searchStr.isClosedForSend) {
            _searchStr.offer(str)
        }
        return true
    }
}

@Singleton
class ArticleListRep @Inject constructor(
    private val service: GbdService,
    private val bannerDao: BannerDao,
    private val articleDao: ArticleDao,
    private val db: MvvmDb

) {

    fun getBanners() = flowNetworkBoundResource(
        loadFromDb = bannerDao::queryAll,
        fetch = service::getBanner,
        saveCallResult = {
            db.runInTransaction {
                bannerDao.deleteAll()
                it?.let { banners ->
                    bannerDao.insertAll(banners)
                }
            }
        }
    ).resMapFlow { res ->
        res.map {
            it.toMutableList().apply {
                add(
                    Banner(
                        -1,
                        "Siy的csdn地址",
                        "https://c-ssl.duitang.com/uploads/item/201702/10/20170210231605_a2HQT.thumb.700_0.png",
                        1,
                        1,
                        "Siy",
                        0,
                        "https://blog.csdn.net/baidu_34012226"
                    )
                )
            }.toList()
        }
    }

    fun getArtclesByPage(search: String) =
        loadFlowDataByPage(
            {
                articleDao.queryBySearchStr(search)
            },
            {
                it
            },
            {
                if (search.isEmpty()) {
                    service.getHomeArticles(it!!)
                } else {
                    //只做本地搜索
                    null
                }
            },
            { list, isRefresh ->
                if (isRefresh) {
                    articleDao.deleteAll()
                }

                if (!list.isNullOrEmpty()) {
                    val sum = articleDao.queryDataSum()

                    articleDao.insertAll(list.mapIndexed { index, article ->
                        article._order_ = sum + index
                        article
                    })
                }
            },
            {
                it?.data?.datas
            }
        )

}