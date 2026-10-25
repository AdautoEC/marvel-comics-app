package com.adauto.marvel

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.adauto.marvel.core.network.di.ApplicationScope
import com.adauto.marvel.core.network.di.ImageOkHttpClient
import com.adauto.marvel.core.network.integrity.ClientIntegrityManager
import com.adauto.marvel.core.network.security.TmdbApiKeyProvider
import com.adauto.marvel.core.network.telemetry.FirebaseTelemetryInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MarvelApp : Application(), ImageLoaderFactory, Configuration.Provider {

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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .crossfade(true)
            .build()
    }
}
