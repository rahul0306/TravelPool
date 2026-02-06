package com.example.travelpool.retrofit

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object FirebaseTokenProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenRef = AtomicReference<String?>(null)

    fun currentToken(): String? = tokenRef.get()

    fun refreshTokenBlocking(): String? {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            tokenRef.set(null)
            return null
        }
        try {
            val task = user.getIdToken(true)
            val tokenResult = Tasks.await(task)
            val token = tokenResult.token
            tokenRef.set(token)
            return token
        } catch (e: Exception) {

            return null
        }
    }

    fun refreshAsync(force: Boolean = false) {
        scope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                tokenRef.set(null)
                return@launch
            }
            try {
                val token = user.getIdToken(force).await().token
                tokenRef.set(token)
            } catch(e: Exception) {
            }
        }
    }
}

class FirebaseAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        fun attach(token: String?): okhttp3.Request {
            if (token.isNullOrBlank()) return request
            return request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        var response = chain.proceed(attach(FirebaseTokenProvider.currentToken()))

        if (response.code == 401 || response.code == 403) {
            response.close()
            val newToken = FirebaseTokenProvider.refreshTokenBlocking()
            response = chain.proceed(attach(newToken))
        }

        return response
    }
}


object WorkerApiFactory {
    private const val BASE_URL = "https://search-worker.travelpool.workers.dev/"

    fun create(): WorkerApi {
        if (FirebaseAuth.getInstance().currentUser != null) {
            FirebaseTokenProvider.refreshAsync(force = false)
        }

        val logging = HttpLoggingInterceptor { msg ->
            // Redact any Authorization lines
            if (msg.startsWith("Authorization: Bearer ")) {
                android.util.Log.d("HTTP", "Authorization: Bearer <redacted>")
            } else {
                android.util.Log.d("HTTP", msg)
            }
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(FirebaseAuthInterceptor())
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WorkerApi::class.java)
    }
}