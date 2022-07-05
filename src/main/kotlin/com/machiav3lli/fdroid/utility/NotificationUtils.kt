package com.machiav3lli.fdroid.utility

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import com.machiav3lli.fdroid.NOTIFICATION_CHANNEL_DOWNLOADING
import com.machiav3lli.fdroid.NOTIFICATION_CHANNEL_INSTALLER
import com.machiav3lli.fdroid.NOTIFICATION_CHANNEL_SYNCING
import com.machiav3lli.fdroid.NOTIFICATION_CHANNEL_UPDATES
import com.machiav3lli.fdroid.NOTIFICATION_ID_DOWNLOADING
import com.machiav3lli.fdroid.NOTIFICATION_ID_INSTALLER
import com.machiav3lli.fdroid.NOTIFICATION_ID_SYNCING
import com.machiav3lli.fdroid.NOTIFICATION_ID_UPDATES
import com.machiav3lli.fdroid.R
import com.machiav3lli.fdroid.database.entity.Repository
import com.machiav3lli.fdroid.entity.ProductItem
import com.machiav3lli.fdroid.index.RepositoryUpdater
import com.machiav3lli.fdroid.installer.InstallerService
import com.machiav3lli.fdroid.service.DownloadService
import com.machiav3lli.fdroid.ui.activities.MainActivityX
import com.machiav3lli.fdroid.utility.extension.android.Android
import com.machiav3lli.fdroid.utility.extension.android.notificationManager
import com.machiav3lli.fdroid.utility.extension.resources.getColorFromAttr

/**
 * Displays summary of available updates.
 *
 * @param productItems list of apps pending updates
 */
fun Context.displayUpdatesNotification(productItems: List<ProductItem>) {
    val maxUpdates = 5
    fun <T> T.applyHack(callback: T.() -> Unit): T = apply(callback)
    notificationManager.notify(
        NOTIFICATION_ID_UPDATES, NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_new_releases)
            .setContentTitle(getString(R.string.new_updates_available))
            .setContentText(
                resources.getQuantityString(
                    R.plurals.new_updates_DESC_FORMAT,
                    productItems.size, productItems.size
                )
            )
            .setColor(
                ContextThemeWrapper(this, R.style.Theme_Main_Light)
                    .getColorFromAttr(android.R.attr.colorPrimary).defaultColor
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivityX::class.java)
                        .setAction(MainActivityX.ACTION_UPDATES)
                        .putExtra(
                            MainActivityX.EXTRA_UPDATES,
                            productItems.map { it.packageName }.toTypedArray()
                        ),
                    if (Android.sdk(23))
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    else
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setStyle(NotificationCompat.InboxStyle().applyHack {
                for (productItem in productItems.take(maxUpdates)) {
                    val builder = SpannableStringBuilder(productItem.name)
                    builder.setSpan(
                        ForegroundColorSpan(Color.BLACK), 0, builder.length,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append(' ').append(productItem.version)
                    addLine(builder)
                }
                if (productItems.size > maxUpdates) {
                    val summary =
                        getString(R.string.plus_more_FORMAT, productItems.size - maxUpdates)
                    if (Android.sdk(24)) {
                        addLine(summary)
                    } else {
                        setSummaryText(summary)
                    }
                }
            })
            .build()
    )
}

fun Context.showNotificationError(repository: Repository, exception: Exception) {
    notificationManager.notify(
        "repository-${repository.id}", NOTIFICATION_ID_SYNCING, NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_SYNCING)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setColor(
                ContextThemeWrapper(this, R.style.Theme_Main_Light)
                    .getColorFromAttr(android.R.attr.colorPrimary).defaultColor
            )
            .setContentTitle(getString(R.string.could_not_sync_FORMAT, repository.name))
            .setContentText(
                getString(
                    when (exception) {
                        is RepositoryUpdater.UpdateException -> when (exception.errorType) {
                            RepositoryUpdater.ErrorType.NETWORK -> R.string.network_error_DESC
                            RepositoryUpdater.ErrorType.HTTP -> R.string.http_error_DESC
                            RepositoryUpdater.ErrorType.VALIDATION -> R.string.validation_index_error_DESC
                            RepositoryUpdater.ErrorType.PARSING -> R.string.parsing_index_error_DESC
                        }
                        else -> R.string.unknown_error_DESC
                    }
                )
            )
            .build()
    )
}

fun Context.showNotificationError(
    task: DownloadService.Task,
    errorType: DownloadService.ErrorType
) {
    notificationManager.notify(task.notificationTag,
        NOTIFICATION_ID_DOWNLOADING,
        NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_DOWNLOADING)
            .setAutoCancel(true)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setColor(
                ContextThemeWrapper(this, R.style.Theme_Main_Light)
                    .getColorFromAttr(R.attr.colorPrimary).defaultColor
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivityX::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("package:${task.packageName}"))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    if (Android.sdk(23))
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    else
                        PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .apply {
                when (errorType) {
                    is DownloadService.ErrorType.Network -> {
                        setContentTitle(
                            getString(
                                R.string.could_not_download_FORMAT,
                                task.name
                            )
                        )
                        setContentText(getString(R.string.network_error_DESC))
                    }
                    is DownloadService.ErrorType.Http -> {
                        setContentTitle(
                            getString(
                                R.string.could_not_download_FORMAT,
                                task.name
                            )
                        )
                        setContentText(getString(R.string.http_error_DESC))
                    }
                    is DownloadService.ErrorType.Validation -> {
                        setContentTitle(
                            getString(
                                R.string.could_not_validate_FORMAT,
                                task.name
                            )
                        )
                        setContentText(
                            getString(
                                when (errorType.validateError) {
                                    DownloadService.ValidationError.INTEGRITY -> R.string.integrity_check_error_DESC
                                    DownloadService.ValidationError.FORMAT -> R.string.file_format_error_DESC
                                    DownloadService.ValidationError.METADATA -> R.string.invalid_metadata_error_DESC
                                    DownloadService.ValidationError.SIGNATURE -> R.string.invalid_signature_error_DESC
                                    DownloadService.ValidationError.PERMISSIONS -> R.string.invalid_permissions_error_DESC
                                }
                            )
                        )
                    }
                }::class
            }
            .build())
}

/**
 * Notifies user of installer outcome. This can be success, error, or a request for user action
 * if installation cannot proceed automatically.
 *
 * @param intent provided by PackageInstaller to the callback service/activity.
 */
fun InstallerService.notifyStatus(intent: Intent?) {
    // unpack from intent
    val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
    val sessionId = intent?.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) ?: 0

    // get package information from session
    val sessionInstaller = this.packageManager.packageInstaller
    val session = if (sessionId > 0) sessionInstaller.getSessionInfo(sessionId) else null

    val name =
        session?.appPackageName ?: intent?.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
    val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    val installerAction = intent?.getStringExtra(InstallerService.KEY_ACTION)

    // get application name for notifications
    val appLabel = session?.appLabel ?: intent?.getStringExtra(InstallerService.KEY_APP_NAME)
    ?: try {
        if (name != null) packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(
                name,
                PackageManager.GET_META_DATA
            )
        ) else null
    } catch (_: Exception) {
        null
    }

    val notificationTag = "${InstallerService.NOTIFICATION_TAG_PREFIX}$name"

    // start building
    val builder = NotificationCompat
        .Builder(this, NOTIFICATION_CHANNEL_INSTALLER)
        .setAutoCancel(true)
        .setColor(
            ContextThemeWrapper(this, R.style.Theme_Main_Light)
                .getColorFromAttr(R.attr.colorPrimary).defaultColor
        )

    when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
            // request user action with "downloaded" notification that triggers a working prompt
            notificationManager.notify(
                notificationTag, NOTIFICATION_ID_INSTALLER, builder
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(installIntent(intent))
                    .setContentTitle(getString(R.string.downloaded_FORMAT, appLabel))
                    .setContentText(getString(R.string.tap_to_install_DESC))
                    .build()
            )
        }
        PackageInstaller.STATUS_SUCCESS -> {
            if (installerAction == InstallerService.ACTION_UNINSTALL)
            // remove any notification for this app
                notificationManager.cancel(notificationTag, NOTIFICATION_ID_INSTALLER)
            else {
                val notification = builder
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(getString(R.string.installed))
                    .setContentText(appLabel)
                    .setTimeoutAfter(InstallerService.INSTALLED_NOTIFICATION_TIMEOUT)
                    .build()
                notificationManager.notify(
                    notificationTag,
                    NOTIFICATION_ID_INSTALLER,
                    notification
                )
            }
        }
        PackageInstaller.STATUS_FAILURE_ABORTED -> {
            // do nothing if user cancels
        }
        else -> {
            // problem occurred when installing/uninstalling package
            val notification = builder
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(getString(R.string.unknown_error_DESC))
                .setContentText(message)
                .build()
            notificationManager.notify(
                notificationTag,
                NOTIFICATION_ID_INSTALLER,
                notification
            )
        }
    }
}