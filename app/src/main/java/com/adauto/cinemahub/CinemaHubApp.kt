package com.adauto.cinemahub

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.adauto.cinemahub.core.network.di.ApplicationScope
import com.adauto.cinemahub.core.network.di.ImageOkHttpClient
import com.adauto.cinemahub.core.network.integrity.ClientIntegrityManager
import com.adauto.cinemahub.core.network.security.TmdbApiKeyProvider
import com.adauto.cinemahub.core.network.telemetry.FirebaseTelemetryInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class CinemaHubApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    @ImageOkHttpClient
    lateinit var imageOkHttpClient: OkHttpClient

    @Inject
    lateinit var clientIntegrityManager: ClientIntegrityManager

    @Inject
    lateinit var firebaseTelemetryInitializer: FirebaseTelemetryInitializer

    @Inject
    lateinit var keyProvider: TmdbApiKeyProvider

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            configureStrictMode()
        }
        runCatching {
            clientIntegrityManager.initializeAppCheck(this, isDebug = BuildConfig.DEBUG)
            firebaseTelemetryInitializer.initialize()
        }
        applicationScope.launch {
            runCatching {
                keyProvider.getCredentials()
            }
        }
    }

    private fun configureStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { imageOkHttpClient }
                    )
                )
            }
            .crossfade(true)
            .build()
    }
}
