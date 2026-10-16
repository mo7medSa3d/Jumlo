package com.universalutilities.app.di

import android.content.Context
import androidx.room.Room
import com.universalutilities.app.data.local.AppDatabase
import com.universalutilities.app.data.local.DownloadTaskDao
import com.universalutilities.app.data.local.ManagedFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideMediaRuntime(@ApplicationContext context: Context): com.universalutilities.app.data.media.MediaRuntime =
        com.universalutilities.app.data.media.MediaRuntimeFactory.create(context)

    @Provides @Singleton fun provideWorkBudget() = com.universalutilities.app.data.jobs.WorkBudget()


    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "universal_utilities_sandbox.db"
        ).addMigrations(com.universalutilities.app.data.local.DatabaseMigrations.FROM_1_TO_2).build()
    }

    @Provides
    fun provideManagedFileDao(db: AppDatabase): ManagedFileDao = db.managedFileDao()

    @Provides
    fun provideDownloadTaskDao(db: AppDatabase): DownloadTaskDao = db.downloadTaskDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
