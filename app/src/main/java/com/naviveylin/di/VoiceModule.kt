package com.naviveylin.di

import com.naviveylin.navigation.AndroidVoiceOutput
import com.naviveylin.navigation.VoiceOutput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the process-scoped device TTS engine to the voice seam the navigation engine consumes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceOutput(
        impl: AndroidVoiceOutput
    ): VoiceOutput
}
