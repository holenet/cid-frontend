package com.cid.bot

import android.content.Context
import android.net.ConnectivityManager
import android.text.TextUtils
import com.cid.bot.data.Message
import com.cid.bot.data.Muser
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import javax.inject.Inject
import javax.inject.Singleton

interface ChatBotAPI {
    @FormUrlEncoded
    @POST("/chatbot/auth/signup/")
    fun signUp(@Field("username") username: String,
               @Field("password") password: String
    ): Observable<Response<JsonObject>>

    @FormUrlEncoded
    @POST("/chatbot/auth/signin/")
    fun signIn(@Field("username") username: String,
               @Field("password") password: String,
               @Field("push_token") pushToken: String
    ): Observable<Response<JsonObject>>

    @POST("/chatbot/auth/signout/")
    fun signOut(): Observable<Response<JsonObject>>

    @FormUrlEncoded
    @POST("/chatbot/auth/withdraw/")
    fun withdraw(@Field("username") username: String,
                 @Field("password") password: String
    ): Observable<Response<JsonObject>>

    @FormUrlEncoded
    @PATCH("/chatbot/my-info/")
    fun changePassword(@Field("old_password") oldPassword: String,
                       @Field("new_password") newPassword: String
    ): Observable<Response<JsonObject>>

    @GET("/chatbot/my-info")
    fun loadMyInfo(): Observable<Response<Muser>>

    @PATCH("/chatbot/my-info/")
    fun saveMyInfo(@Body muser: Muser): Observable<Response<Muser>>

    @GET("/chatbot/chat/")
    fun loadAllMessages(): Observable<Response<List<Message>>>

    @GET("/chatbot/chat/{id}/")
    fun loadMessage(@Path("id") id: Int): Observable<Response<Message>>

    @FormUrlEncoded
    @POST("/chatbot/chat/")
    fun sendMessage(@Field("text") text: String): Observable<Response<Message>>
}

fun String?.toMap(): Map<String, String> = HashMap<String, String>().apply {
    val errorString = if (this@toMap.isNullOrBlank()) "{\"_\":\"Unknown Error\"}" else this@toMap
    val jsonObject = JSONObject(errorString)
    for (key in jsonObject.keys()) {
        val list = try {
            jsonObject.getJSONArray(key)
        } catch (e: JSONException) {
            JSONArray("[\"${jsonObject.get(key)}\"]")
        }
        val strings = mutableListOf<String>()
        for (i in 0 until list.length()) {
            strings += list.get(i).toString()
        }

        this[key] = TextUtils.join("\n", strings)
    }
}

fun <T> Observable<Response<T>>.toHResult(): Observable<HResult<T>> {
    return map { response ->
        if (response.isSuccessful)
            HResult(response.body()!!)
        else
            HResult(response.errorBody()?.string().toMap())
    }.onErrorResumeNext(Function {
        it.printStackTrace()
        it.message?.let { Observable.just(HResult(mapOf("exception" to it))) }
    })
}

const val BASE_URL = "http://52.78.179.149"
//const val BASE_URL = "http://10.0.2.2:8000" /* development environment */

@Singleton
class NetworkManager @Inject constructor(private val context: Context) {
    var authToken: String? = null
    private val interceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder().header("Authorization", if (authToken == null) "" else "Token $authToken")
        val request = builder.build()
        chain.proceed(request)
    }
    val api: ChatBotAPI = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(
                    GsonBuilder().serializeNulls().create()
            ))
            .client(OkHttpClient.Builder().addInterceptor(interceptor).build())
            .build().create(ChatBotAPI::class.java)
    val isConnectedToInternet: Boolean
        get() {
            val conManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val ni = conManager.activeNetworkInfo
            return ni != null && ni.isConnected
        }

    /**
     * Helper function of API methods.
     * Provide simple abstraction of observations.
     *
     * @param observable Response object returned from API methods
     * @param onSuccess  Called on success response(200)
     * @param onError    Called on error response(not 200)
     * @param onFinish   Called on finish whichever success or error
     * @return A disposable object. By disposing it, you can cancel subscripting.
     */
    fun <T> call(observable: Observable<Response<T>>,
                 onSuccess: (T) -> Unit,
                 onError: (Map<String, String>) -> Unit,
                 onFinish: () -> Unit = {}
    ): Disposable {
        return observable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ response ->
                    onFinish()
                    if (response.isSuccessful)
                        response.body()?.also { onSuccess(it) }
                    else {
                        onError(response.errorBody()?.string().toMap())
                    }
                }, { error ->
                    onFinish()
                    error.message?.also { onError(_networkError) }
                })
    }

    private val _networkError = mapOf("Network" to "Failed to connect to the server")
    fun <T> getNetworkError() = HResult<T>(_networkError)
}
