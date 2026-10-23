package com.adauto.marvel

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.adauto.marvel.core.network.di.ImageOkHttpClient
import com.adauto.marvel.core.network.integrity.ClientIntegrityManager
import com.adauto.marvel.core.network.telemetry.FirebaseTelemetryInitializer
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MarvelApp : Application(), ImageLoaderFactory {

    @Inject
    @ImageOkHttpClient
    lateinit var imageOkHttpClient: OkHttpClient

    @Inject
    lateinit var clientIntegrityManager: ClientIntegrityManager

    @Inject
    lateinit var firebaseTelemetryInitializer: FirebaseTelemetryInitializer

    override fun onCreate() {
        super.onCreate()
        runCatching {
            clientIntegrityManager.initializeAppCheck(this, isDebug = BuildConfig.DEBUG)
            firebaseTelemetryInitializer.initialize()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .crossfade(true)
            .build()
    }
}
