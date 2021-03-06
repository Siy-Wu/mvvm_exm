package com.siy.mvvm.exm.ui.login

import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.navOptions
import com.siy.mvvm.exm.R
import com.siy.mvvm.exm.base.Injectable
import com.siy.mvvm.exm.base.ui.BaseFragment
import com.siy.mvvm.exm.base.ui.navigateAnimate
import com.siy.mvvm.exm.databinding.FragmentLoginBinding
import com.siy.mvvm.exm.utils.Event
import com.siy.mvvm.exm.utils.EventObserver
import com.siy.mvvm.exm.utils.showToast
import javax.inject.Inject

/**
 * Created by Siy on 2019/08/08.
 *
 * @author Siy
 */
class LoginFragment(
    override val layoutId: Int = R.layout.fragment_login
) : BaseFragment<FragmentLoginBinding>(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel: LoginViewModel by viewModels {
        viewModelFactory
    }

    override fun initViewsAndEvents(view: View) {
        mViewDataBinding.viewModel = viewModel

        viewModel.toastEvent.observe(viewLifecycleOwner, EventObserver {
            showToast(it)
        })

        viewModel.loginSate.observe(viewLifecycleOwner) {
            when (it) {
                LoginViewModel.LoginState.LOGINED -> {
                    navController.navigateAnimate(LoginFragmentDirections.actionLoginFragmentToMainFragment(),
                        navOptions {
                            popUpTo(R.id.loginFragment) {
                                inclusive = true
                            }
                        })
                }
                LoginViewModel.LoginState.LOADFAIL -> {
                    showToast("登录失败")
                }
                else -> Unit
            }
        }

    }
}

class LoginViewModel @Inject constructor() : ViewModel() {

    enum class LoginState {
        /**
         * 登录成功
         */
        LOGINED,

        /**
         * 登录中
         */
        LOADING,

        /**
         * 登录失败
         */
        LOADFAIL
    }

    /**
     * 用户名双向绑定
     */
    val mUserName = MutableLiveData<String>("椿")

    /**
     * 密码双向绑定
     */
    val mUserPwd = MutableLiveData<String>("湫")

    /**
     * 是否显示密码
     */
    private val _showPwd = MutableLiveData(false)
    val showPwd: LiveData<Boolean>
        get() = _showPwd

    /**
     * Toast
     */
    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>>
        get() = _toastEvent

    private val _loginReq = MutableLiveData<Pair<String, String>>()
    /**
     * 登录的状态
     */
    val loginSate = _loginReq.map {
        if (it.first == "椿" && it.second == "湫") {
            LoginState.LOGINED
        } else {
            LoginState.LOADFAIL
        }
    }


    fun hideOrShowPwd() {
        _showPwd.value = !(_showPwd.value ?: false)
    }

    fun login() {
        val userName = mUserName.value
        val userPwd = mUserPwd.value
        if (userName.isNullOrEmpty() || userPwd.isNullOrEmpty()) {
            _toastEvent.value = Event("请输入用户名或密码")
            return
        }

        _loginReq.value = Pair(userName, userPwd)
    }
}