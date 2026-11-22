package com.legode.friedrich.di

import android.content.Context
import androidx.room.Room
import com.legode.friedrich.audio.Speaker
import com.legode.friedrich.brain.Brain
import com.legode.friedrich.brain.GeminiClient
import com.legode.friedrich.brain.KnowledgeBase
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.RuntimeState
import com.legode.friedrich.data.SettingsStore
import com.legode.friedrich.data.db.AppDatabase
import com.legode.friedrich.notify.AlarmScheduler
import com.legode.friedrich.notify.Notifier
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Весь граф зависимостей Атласа — в одном месте. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "atlas.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)

    @Provides
    @Singleton
    fun provideRuntime(@ApplicationContext context: Context): RuntimeState = RuntimeState(context)

    @Provides
    @Singleton
    fun provideAttachments(@ApplicationContext context: Context): AttachmentStore = AttachmentStore(context)

    @Provides
    @Singleton
    fun provideKnowledgeBase(@ApplicationContext context: Context): KnowledgeBase = KnowledgeBase(context)

    @Provides
    @Singleton
    fun provideRepository(db: AppDatabase, attachments: AttachmentStore): Repository = Repository(db, attachments)

    @Provides
    @Singleton
    fun provideGemini(): GeminiClient = GeminiClient()

    @Provides
    @Singleton
    fun provideNotifier(@ApplicationContext context: Context): Notifier = Notifier(context)

    @Provides
    @Singleton
    fun provideAlarms(@ApplicationContext context: Context): AlarmScheduler = AlarmScheduler(context)

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSpeaker(@ApplicationContext context: Context): Speaker = Speaker(context)

    @Provides
    @Singleton
    fun provideBrain(
        @ApplicationContext context: Context,
        repo: Repository,
        gemini: GeminiClient,
        settings: SettingsStore,
        runtime: RuntimeState,
        attachments: AttachmentStore,
        kb: KnowledgeBase,
        notifier: Notifier,
        alarms: AlarmScheduler,
        scope: CoroutineScope
    ): Brain = Brain(context, repo, gemini, settings, runtime, attachments, kb, notifier, alarms, scope)
}

/** Доступ к графу из мест, куда Hilt не внедряет сам: воркеры, приёмники, сервис. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppGraph {
    fun brain(): Brain
    fun repo(): Repository
    fun settings(): SettingsStore
    fun runtime(): RuntimeState
    fun notifier(): Notifier
    fun alarms(): AlarmScheduler
    fun kb(): KnowledgeBase
    fun scope(): CoroutineScope
}

fun Context.graph(): AppGraph =
    EntryPointAccessors.fromApplication(applicationContext, AppGraph::class.java)