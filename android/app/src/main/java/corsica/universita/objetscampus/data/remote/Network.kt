package corsica.universita.objetscampus.data.remote

import android.content.Context
import corsica.universita.objetscampus.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Singleton reseau Retrofit + OkHttp avec cache HTTP de 10 Mo dans cacheDir/http (TD A5).
 */
object Network {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Volatile
    private var apiInstance: SyncApi? = null

    fun create(context: Context): SyncApi =
        apiInstance ?: synchronized(this) {
            apiInstance ?: buildApi(context.applicationContext).also { apiInstance = it }
        }

    private fun buildApi(context: Context): SyncApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024)) // 10 Mo
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.SYNC_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SyncApi::class.java)
    }
}
