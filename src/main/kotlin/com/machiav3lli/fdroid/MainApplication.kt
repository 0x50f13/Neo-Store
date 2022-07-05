package com.machiav3lli.fdroid

import android.annotation.SuppressLint
import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.machiav3lli.fdroid.content.Cache
import com.machiav3lli.fdroid.content.Preferences
import com.machiav3lli.fdroid.database.DatabaseX
import com.machiav3lli.fdroid.index.RepositoryUpdater
import com.machiav3lli.fdroid.network.CoilDownloader
import com.machiav3lli.fdroid.network.Downloader
import com.machiav3lli.fdroid.service.Connection
import com.machiav3lli.fdroid.service.PackageChangedReceiver
import com.machiav3lli.fdroid.service.SyncService
import com.machiav3lli.fdroid.ui.activities.MainActivityX
import com.machiav3lli.fdroid.utility.Utils.setLanguage
import com.machiav3lli.fdroid.utility.Utils.toInstalledItem
import com.machiav3lli.fdroid.utility.extension.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.Duration.Companion.minutes


@Suppress("unused")
class MainApplication : Application(), ImageLoaderFactory {

    lateinit var db: DatabaseX
    //lateinit var wm: WorksManager

    companion object {
        private var appRef: WeakReference<MainApplication> = WeakReference(null)
        private val neo_store: MainApplication get() = appRef.get()!!

        //val wm: WorksManager get() = neo_store.wm
        //val db: DatabaseX get() = neo_store.db
    }

    override fun onCreate() {
        super.onCreate()
        appRef = WeakReference(this)

        db = DatabaseX.getInstance(applicationContext)
        Preferences.init(this)
        RepositoryUpdater.init(this)
        listenApplications()
        listenPreferences()

        /*if (databaseUpdated) {
            forceSyncAll()
        }*/

        //wm = WorksManager(applicationContext)
        //wm.prune()
        Cache.cleanup(this)
        updateSyncJob(false)
    }

    private fun listenApplications() {
        registerReceiver(
            PackageChangedReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        )
        val launcherActivitiesMap =
            packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0
                )
                .mapNotNull { resolveInfo -> resolveInfo.activityInfo }
                .groupBy { it.packageName }
                .mapNotNull { (packageName, activityInfos) ->
                    val aiNameLabels = activityInfos.mapNotNull {
                        val label = try {
                            it.loadLabel(packageManager).toString()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                        label?.let { label -> Pair(it.name, label) }
                    }
                    Pair(packageName, aiNameLabels)
                }.toMap()
        val installedItems = packageManager
            .getInstalledPackages(Android.PackageManager.signaturesFlag)
            .map { it.toInstalledItem(launcherActivitiesMap[it.packageName].orEmpty()) }
        CoroutineScope(Dispatchers.Default).launch {
            db.installedDao.emptyTable()
            db.installedDao.put(*installedItems.toTypedArray())
        }
    }

    private fun listenPreferences() {
        updateProxy()
        var lastAutoSync = Preferences[Preferences.Key.AutoSync]
        var lastUpdateUnstable = Preferences[Preferences.Key.UpdateUnstable]
        var lastLanguage = Preferences[Preferences.Key.Language]
        CoroutineScope(Dispatchers.Default).launch {
            Preferences.subject.collect {
                if (it == Preferences.Key.ProxyType || it == Preferences.Key.ProxyHost || it == Preferences.Key.ProxyPort) {
                    updateProxy()
                } else if (it == Preferences.Key.AutoSync) {
                    val autoSync = Preferences[Preferences.Key.AutoSync]
                    if (lastAutoSync != autoSync) {
                        lastAutoSync = autoSync
                        updateSyncJob(true)
                    }
                } else if (it == Preferences.Key.UpdateUnstable) {
                    val updateUnstable = Preferences[Preferences.Key.UpdateUnstable]
                    if (lastUpdateUnstable != updateUnstable) {
                        lastUpdateUnstable = updateUnstable
                        forceSyncAll()
                    }
                } else if (it == Preferences.Key.Language) {
                    val language = Preferences[Preferences.Key.Language]
                    if (language != lastLanguage) {
                        lastLanguage = language
                        val refresh = Intent.makeRestartActivityTask(
                            ComponentName(
                                baseContext,
                                MainActivityX::class.java
                            )
                        )
                        applicationContext.startActivity(refresh)
                    }
                }
            }
        }
    }

    private fun updateSyncJob(force: Boolean) {
        val jobScheduler = getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        val reschedule = force || !jobScheduler.allPendingJobs.any { it.id == JOB_ID_SYNC }
        if (reschedule) {
            val autoSync = Preferences[Preferences.Key.AutoSync]
            when (autoSync) {
                is Preferences.AutoSync.Never -> {
                    jobScheduler.cancel(JOB_ID_SYNC)
                }
                is Preferences.AutoSync.Wifi -> {
                    autoSync(
                        jobScheduler = jobScheduler,
                        connectionType = JobInfo.NETWORK_TYPE_UNMETERED
                    )
                }
                is Preferences.AutoSync.WifiBattery -> {
                    if (isCharging(this)) {
                        autoSync(
                            jobScheduler = jobScheduler,
                            connectionType = JobInfo.NETWORK_TYPE_UNMETERED
                        )
                    }
                    Unit
                }
                is Preferences.AutoSync.Always -> {
                    autoSync(
                        jobScheduler = jobScheduler,
                        connectionType = JobInfo.NETWORK_TYPE_ANY
                    )
                }
            }::class.java
        }
    }

    private fun autoSync(jobScheduler: JobScheduler, connectionType: Int) {
        val period = 5.minutes.inWholeMilliseconds
        jobScheduler.schedule(
            JobInfo
                .Builder(
                    JOB_ID_SYNC,
                    ComponentName(this, SyncService.Job::class.java)
                )
                .setRequiredNetworkType(connectionType)
                .apply {
                    if (Android.sdk(26)) {
                        setRequiresBatteryNotLow(true)
                        setRequiresStorageNotLow(true)
                    }
                    if (Android.sdk(24)) setPeriodic(period, JobInfo.getMinFlexMillis())
                    else setPeriodic(period)
                }
                .build()
        )
    }

    private fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent!!.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        return plugged == BatteryManager.BATTERY_PLUGGED_AC
                || plugged == BatteryManager.BATTERY_PLUGGED_USB
                || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    private fun updateProxy() {
        val type = Preferences[Preferences.Key.ProxyType].proxyType
        val host = Preferences[Preferences.Key.ProxyHost]
        val port = Preferences[Preferences.Key.ProxyPort]
        val socketAddress = when (type) {
            Proxy.Type.DIRECT -> {
                null
            }
            Proxy.Type.HTTP, Proxy.Type.SOCKS -> {
                try {
                    InetSocketAddress.createUnresolved(host, port)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
        val proxy = socketAddress?.let { Proxy(type, it) }
        Downloader.proxy = proxy
    }

    private fun forceSyncAll() {
        db.repositoryDao.all.forEach {
            if (it.lastModified.isNotEmpty() || it.entityTag.isNotEmpty()) {
                db.repositoryDao.put(it.copy(lastModified = "", entityTag = ""))
            }
        }
        Connection(SyncService::class.java, onBind = { connection, binder ->
            binder.sync(SyncService.SyncRequest.FORCE)
            connection.unbind(this)
        }).bind(this)
    }

    class BootReceiver : BroadcastReceiver() {
        @SuppressLint("UnsafeProtectedBroadcastReceiver")
        override fun onReceive(context: Context, intent: Intent) = Unit
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .callFactory(CoilDownloader.Factory(Cache.getImagesDir(this)))
            .crossfade(true)
            .build()
    }
}

class ContextWrapperX(base: Context) : ContextWrapper(base) {
    companion object {
        fun wrap(context: Context): ContextWrapper {
            val config = context.setLanguage()
            return ContextWrapperX(context.createConfigurationContext(config))
        }
    }
}