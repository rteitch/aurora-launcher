package app.aurora.launcher.core.data.di

import android.content.Context
import androidx.room.Room
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.data.db.AuroraDao
import app.aurora.launcher.core.data.db.AuroraDatabase
import app.aurora.launcher.core.data.repository.AppCatalogRepository
import app.aurora.launcher.core.data.repository.AppCatalogRepositoryImpl
import app.aurora.launcher.core.data.repository.BackupRepository
import app.aurora.launcher.core.data.repository.BackupRepositoryImpl
import app.aurora.launcher.core.data.repository.LauncherSettingsRepository
import app.aurora.launcher.core.data.repository.LauncherSettingsRepositoryImpl
import app.aurora.launcher.core.data.repository.SearchHistoryRepository
import app.aurora.launcher.core.data.repository.SearchHistoryRepositoryImpl
import app.aurora.launcher.core.data.repository.WorkspaceRepository
import app.aurora.launcher.core.data.repository.WorkspaceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseProvidesModule {
    @Provides
    @Singleton
    fun provideAuroraDatabase(
        @ApplicationContext context: Context,
    ): AuroraDatabase {
        return Room.databaseBuilder(
            context,
            AuroraDatabase::class.java,
            "aurora_launcher.db",
        )
            .addCallback(AuroraDatabase.PartialIndexCallback)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuroraDao(database: AuroraDatabase): AuroraDao = database.auroraDao()

    @Provides
    @Singleton
    fun provideAuroraDispatchers(): AuroraDispatchers = AuroraDispatchers()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: LauncherSettingsRepositoryImpl): LauncherSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppCatalogRepository(impl: AppCatalogRepositoryImpl): AppCatalogRepository

    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(impl: WorkspaceRepositoryImpl): WorkspaceRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(impl: SearchHistoryRepositoryImpl): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
