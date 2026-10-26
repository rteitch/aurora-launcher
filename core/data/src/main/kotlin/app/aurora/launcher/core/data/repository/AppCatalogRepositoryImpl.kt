package app.aurora.launcher.core.data.repository

import androidx.room.withTransaction
import app.aurora.launcher.core.data.db.AppEntryEntity
import app.aurora.launcher.core.data.db.AppUsageDailyEntity
import app.aurora.launcher.core.data.db.AuroraDao
import app.aurora.launcher.core.data.db.AuroraDatabase
import app.aurora.launcher.core.data.db.SearchHistoryEntity
import app.aurora.launcher.core.model.AppEntry
import app.aurora.launcher.core.model.ComponentKey
import app.aurora.launcher.core.model.LibraryGroup
import app.aurora.launcher.core.model.ProfileBadge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCatalogRepositoryImpl @Inject constructor(
    private val database: AuroraDatabase,
    private val dao: AuroraDao,
) : AppCatalogRepository {

    override val allApps: Flow<List<AppEntry>> = dao.observeApps().map { list ->
        list.map { it.toDomain() }
    }

    override val recentlyAddedApps: Flow<List<AppEntry>> = allApps.map { list ->
        list.filter { !it.isHidden }
            .sortedByDescending { it.installTimeEpochMs }
            .take(8)
    }

    override val frequentlyUsedApps: Flow<List<AppEntry>> = allApps.map { list ->
        list.filter { !it.isHidden && it.launchCount > 0 }
            .sortedWith(
                compareByDescending<AppEntry> { it.launchCount }
                    .thenByDescending { it.lastLaunchEpochMs ?: 0L },
            )
            .take(8)
    }

    override suspend fun syncInstalledApps(installed: List<AppEntry>) {
        database.withTransaction {
            val existingByKey = dao.getApps().associateBy {
                ComponentKey(it.packageName, it.activityClass, it.userSerial)
            }
            val merged = installed.map { incoming ->
                val prev = existingByKey[incoming.key]
                AppEntryEntity(
                    packageName = incoming.key.packageName,
                    activityClass = incoming.key.activityClass,
                    userSerial = incoming.key.userSerial,
                    label = incoming.label,
                    labelSortKey = incoming.sortKey,
                    category = incoming.systemCategory,
                    libraryGroup = incoming.libraryGroup.name,
                    userGroupOverride = prev?.userGroupOverride ?: incoming.userGroupOverride?.name,
                    isHidden = prev?.isHidden ?: incoming.isHidden,
                    isLocked = prev?.isLocked ?: incoming.isLocked,
                    isSystem = incoming.isSystemApp,
                    isSuspended = incoming.isSuspended,
                    isQuietPaused = incoming.isQuietModePaused,
                    profileBadge = incoming.profileBadge.name,
                    installTime = incoming.installTimeEpochMs,
                    updateTime = incoming.updateTimeEpochMs,
                    launchCount = prev?.launchCount ?: incoming.launchCount,
                    lastLaunchAt = prev?.lastLaunchAt ?: incoming.lastLaunchEpochMs,
                )
            }
            val incomingKeys = installed.map { it.key }.toSet()
            for (oldKey in existingByKey.keys) {
                if (oldKey !in incomingKeys) {
                    dao.deleteAppEntry(oldKey.packageName, oldKey.activityClass, oldKey.userSerial)
                }
            }
            if (merged.isNotEmpty()) {
                dao.upsertApps(merged)
            }
        }
    }

    override suspend fun purgePrivateSpaceApps() {
        dao.deletePrivateSpaceApps()
    }

    override suspend fun recordAppLaunch(key: ComponentKey, epochMs: Long) {
        val epochDay = epochMs / MILLIS_PER_DAY
        database.withTransaction {
            dao.incrementLaunchCount(key.packageName, key.activityClass, key.userSerial, epochMs)
            val prevDaily = dao.getDailyUsage(key.packageName, epochDay)
            dao.upsertDailyUsage(
                AppUsageDailyEntity(
                    packageName = key.packageName,
                    day = epochDay,
                    launches = (prevDaily?.launches ?: 0) + 1,
                ),
            )
            dao.pruneDailyUsageBefore(epochDay - RETENTION_DAYS)
        }
    }

    override suspend fun setAppHidden(key: ComponentKey, hidden: Boolean) {
        dao.updateAppHidden(key.packageName, key.activityClass, key.userSerial, hidden)
    }

    override suspend fun setAppLocked(key: ComponentKey, locked: Boolean) {
        dao.updateAppLocked(key.packageName, key.activityClass, key.userSerial, locked)
    }

    override suspend fun setCategoryOverride(key: ComponentKey, group: LibraryGroup?) {
        dao.updateAppCategoryOverride(key.packageName, key.activityClass, key.userSerial, group?.name)
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val RETENTION_DAYS = 30L
    }
}

@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val database: AuroraDatabase,
    private val dao: AuroraDao,
) : SearchHistoryRepository {

    override val recentQueries: Flow<List<String>> = dao.observeSearchHistory().map { rows ->
        rows.map { it.query }
    }

    override suspend fun recordQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        database.withTransaction {
            dao.deleteSearchQuery(trimmed)
            dao.insertSearchHistory(SearchHistoryEntity(query = trimmed, ts = System.currentTimeMillis()))
            dao.trimSearchHistoryTo50()
        }
    }

    override suspend fun clearHistory() {
        dao.clearSearchHistory()
    }
}

internal fun AppEntryEntity.toDomain(): AppEntry = AppEntry(
    key = ComponentKey(packageName, activityClass, userSerial),
    label = label,
    sortKey = labelSortKey,
    systemCategory = category,
    libraryGroup = runCatching { LibraryGroup.valueOf(libraryGroup) }.getOrDefault(LibraryGroup.OTHER),
    userGroupOverride = userGroupOverride?.let { runCatching { LibraryGroup.valueOf(it) }.getOrNull() },
    isHidden = isHidden,
    isLocked = isLocked,
    isSystemApp = isSystem,
    isSuspended = isSuspended,
    isQuietModePaused = isQuietPaused,
    profileBadge = runCatching { ProfileBadge.valueOf(profileBadge) }.getOrDefault(ProfileBadge.PERSONAL),
    installTimeEpochMs = installTime,
    updateTimeEpochMs = updateTime,
    launchCount = launchCount,
    lastLaunchEpochMs = lastLaunchAt,
)
