package com.legode.app.di

import android.content.Context
import androidx.room.Room
import com.legode.app.data.db.AppDatabase
import com.legode.app.data.db.CbtDao
import com.legode.app.data.db.SportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "psysport_master.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideCbtDao(db: AppDatabase): CbtDao = db.cbtDao()

    @Provides
    fun provideSportDao(db: AppDatabase): SportDao = db.sportDao()
}