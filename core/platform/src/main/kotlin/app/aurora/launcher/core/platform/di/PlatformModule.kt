package app.aurora.launcher.core.platform.di

import android.content.Context
import app.aurora.launcher.core.common.AuroraDispatchers
import app.aurora.launcher.core.platform.AppWidgetHostManager
import app.aurora.launcher.core.platform.BiometricAuthGateway
import app.aurora.launcher.core.platform.ContactsAndSettingsSearchGateway
import app.aurora.launcher.core.platform.ControlPanelGateway
import app.aurora.launcher.core.platform.IconCacheAndLoader
import app.aurora.launcher.core.platform.IconPackGateway
import app.aurora.launcher.core.platform.LauncherAppsGateway
import app.aurora.launcher.core.platform.NotificationBadgeGateway
import app.aurora.launcher.core.platform.RoleGateway
import app.aurora.launcher.core.platform.WallpaperGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    @Singleton
    fun provideRoleGateway(
        @ApplicationContext context: Context,
    ): RoleGateway = RoleGateway(context)

    @Provides
    @Singleton
    fun provideBiometricAuthGateway(
        @ApplicationContext context: Context,
    ): BiometricAuthGateway = BiometricAuthGateway(context)

    @Provides
    @Singleton
    fun provideNotificationBadgeGateway(
        @ApplicationContext context: Context,
    ): NotificationBadgeGateway = NotificationBadgeGateway(context)

    @Provides
    @Singleton
    fun provideLauncherAppsGateway(
        @ApplicationContext context: Context,
        dispatchers: AuroraDispatchers,
    ): LauncherAppsGateway = LauncherAppsGateway(context, dispatchers)

    @Provides
    @Singleton
    fun provideWallpaperGateway(
        @ApplicationContext context: Context,
        dispatchers: AuroraDispatchers,
    ): WallpaperGateway = WallpaperGateway(context, dispatchers)

    @Provides
    @Singleton
    fun provideIconPackGateway(
        @ApplicationContext context: Context,
        dispatchers: AuroraDispatchers,
    ): IconPackGateway = IconPackGateway(context, dispatchers)

    @Provides
    @Singleton
    fun provideContactsAndSettingsSearchGateway(
        @ApplicationContext context: Context,
        dispatchers: AuroraDispatchers,
    ): ContactsAndSettingsSearchGateway = ContactsAndSettingsSearchGateway(context, dispatchers)

    @Provides
    @Singleton
    fun provideAppWidgetHostManager(
        @ApplicationContext context: Context,
        dispatchers: AuroraDispatchers,
    ): AppWidgetHostManager = AppWidgetHostManager(context, dispatchers)

    @Provides
    @Singleton
    fun provideIconCacheAndLoader(
        @ApplicationContext context: Context,
        iconPackGateway: IconPackGateway,
        dispatchers: AuroraDispatchers,
    ): IconCacheAndLoader = IconCacheAndLoader(context, iconPackGateway, dispatchers)

    @Provides
    @Singleton
    fun provideControlPanelGateway(
        @ApplicationContext context: Context,
    ): ControlPanelGateway = ControlPanelGateway(context)
}
