package com.tkdpro.di

import android.content.Context
import androidx.room.Room
import com.tkdpro.core.database.TkdDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TkdDatabase =
        Room.databaseBuilder(context, TkdDatabase::class.java, TkdDatabase.NAME).build()

    @Provides fun trainingDao(db: TkdDatabase) = db.trainingDao()
    @Provides fun techniqueDao(db: TkdDatabase) = db.techniqueDao()
    @Provides fun combinationDao(db: TkdDatabase) = db.combinationDao()
    @Provides fun videoDao(db: TkdDatabase) = db.videoDao()
    @Provides fun motionDao(db: TkdDatabase) = db.motionDao()
    @Provides fun matchDao(db: TkdDatabase) = db.matchDao()
    @Provides fun competitionDao(db: TkdDatabase) = db.competitionDao()
    @Provides fun scoringPresetDao(db: TkdDatabase) = db.scoringPresetDao()
    @Provides fun runningDao(db: TkdDatabase) = db.runningDao()
    @Provides fun reactionDao(db: TkdDatabase) = db.reactionDao()
    @Provides fun performanceDao(db: TkdDatabase) = db.performanceDao()
    @Provides fun recoveryDao(db: TkdDatabase) = db.recoveryDao()
    @Provides fun profileDao(db: TkdDatabase) = db.profileDao()
    @Provides fun goalDao(db: TkdDatabase) = db.goalDao()
    @Provides fun aiDao(db: TkdDatabase) = db.aiDao()
    @Provides fun syncDao(db: TkdDatabase) = db.syncDao()
    @Provides fun syncApplyDao(db: TkdDatabase) = db.syncApplyDao()

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun clock(): Clock = Clock.System
}

/** Injectable time source so business logic can be tested deterministically. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
