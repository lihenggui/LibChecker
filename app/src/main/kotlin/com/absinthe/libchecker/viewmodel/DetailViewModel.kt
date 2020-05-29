package com.absinthe.libchecker.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.absinthe.libchecker.R
import com.absinthe.libchecker.api.ApiManager
import com.absinthe.libchecker.api.bean.NativeLibDetailBean
import com.absinthe.libchecker.api.request.NativeLibDetailRequest
import com.absinthe.libchecker.constant.GlobalValues
import com.absinthe.libchecker.constant.librarymap.NativeLibMap
import com.absinthe.libchecker.constant.librarymap.ServiceLibMap
import com.absinthe.libchecker.ui.fragment.applist.MODE_SORT_BY_SIZE
import com.absinthe.libchecker.utils.PackageUtils
import com.absinthe.libchecker.bean.LibStringItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    val libItems: MutableLiveData<ArrayList<LibStringItem>> = MutableLiveData()
    val componentsItems: MutableLiveData<ArrayList<LibStringItem>> = MutableLiveData()
    val detailBean: MutableLiveData<NativeLibDetailBean?> = MutableLiveData()

    fun initSoAnalysisData(context: Context, packageName: String) =
        viewModelScope.launch(Dispatchers.IO) {
            val list = ArrayList<LibStringItem>()

            try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)

                list.addAll(
                    getAbiByNativeDir(
                        context,
                        info.sourceDir,
                        info.nativeLibraryDir
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                list.add(LibStringItem("Not found", 0))
            }

            withContext(Dispatchers.Main) {
                libItems.value = list
            }
        }

    fun initComponentsData(context: Context, packageName: String) =
        viewModelScope.launch(Dispatchers.IO) {
            val list = ArrayList<LibStringItem>()

            try {
                val packageInfo =
                    context.packageManager.getPackageInfo(packageName, PackageManager.GET_SERVICES)
                for (service in packageInfo.services) {
                    list.add(LibStringItem(service.name.removePrefix(packageName), 0))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                list.add(LibStringItem("Not found", 0))
            }

            if (GlobalValues.libSortMode.value == MODE_SORT_BY_SIZE) {
                list.sortByDescending { it.size }
            } else {
                list.sortByDescending {
                    ServiceLibMap.MAP.containsKey(
                        it.name
                    )
                }
            }

            withContext(Dispatchers.Main) {
                componentsItems.value = list
            }
        }

    fun requestNativeLibDetail(libName: String) = viewModelScope.launch(Dispatchers.IO) {
        val retrofit = Retrofit.Builder()
            .baseUrl(ApiManager.root)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val request = retrofit.create(NativeLibDetailRequest::class.java)
        val detail = request.requestNativeLibDetail("native-libs/${libName}.json")
        Log.d("DetailViewModel", "requestNativeLibDetail: root = ${ApiManager.root}")
        detail.enqueue(object : Callback<NativeLibDetailBean> {
            override fun onFailure(call: Call<NativeLibDetailBean>, t: Throwable) {
                Log.e("DetailViewModel", t.message ?: "")
                detailBean.value = null
            }

            override fun onResponse(
                call: Call<NativeLibDetailBean>,
                response: Response<NativeLibDetailBean>
            ) {
                detailBean.value = response.body()
            }
        })
    }

    private fun getAbiByNativeDir(
        context: Context,
        sourcePath: String,
        nativePath: String
    ): List<LibStringItem> {
        val list = PackageUtils.getAbiByNativeDir(sourcePath, nativePath).toMutableList()

        if (list.isEmpty()) {
            list.add(LibStringItem(context.getString(R.string.empty_list), 0))
        } else {
            if (GlobalValues.libSortMode.value == MODE_SORT_BY_SIZE) {
                list.sortByDescending { it.size }
            } else {
                list.sortByDescending {
                    NativeLibMap.MAP.containsKey(
                        it.name
                    )
                }
            }
        }
        return list
    }
}