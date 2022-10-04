package com.machiav3lli.fdroid.entity

import android.content.Context
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.machiav3lli.fdroid.R
import com.machiav3lli.fdroid.content.Preferences
import com.machiav3lli.fdroid.ui.compose.icons.Phosphor
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.ArrowSquareOut
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.Download
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.HeartStraight
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.HeartStraightFill
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.ShareNetwork
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.SlidersHorizontal
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.TrashSimple
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.X
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Author(val name: String = "", val email: String = "", val web: String = "") {
    fun toJSON() = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String) = Json.decodeFromString<Author>(json)
    }
}

@Serializable
sealed class Donate {
    @Serializable
    data class Regular(val url: String) : Donate()

    @Serializable
    data class Bitcoin(val address: String) : Donate()

    @Serializable
    data class Litecoin(val address: String) : Donate()

    @Serializable
    data class Flattr(val id: String) : Donate()

    @Serializable
    data class Liberapay(val id: String) : Donate()

    @Serializable
    data class OpenCollective(val id: String) : Donate()

    fun toJSON() = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String) = Json.decodeFromString<Donate>(json)
    }
}

@Serializable
class Screenshot(val locale: String, val type: Type, val path: String) {
    enum class Type(val jsonName: String) {
        PHONE("phone"),
        SMALL_TABLET("smallTablet"),
        LARGE_TABLET("largeTablet")
    }

    val identifier: String
        get() = "$locale.${type.name}.$path"

    fun toJSON() = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String) = Json.decodeFromString<Screenshot>(json)
    }
}

enum class AntiFeature(val key: String, @StringRes val titleResId: Int) {
    ADS("Ads", R.string.has_advertising),
    DEBUGGABLE("ApplicationDebuggable", R.string.compiled_for_debugging),
    DISABLED_ALGORITHM("DisabledAlgorithm", R.string.signed_using_unsafe_algorithm),
    KNOWN_VULN("KnownVuln", R.string.has_security_vulnerabilities),
    NO_SOURCE_SINCE("NoSourceSince", R.string.source_code_no_longer_available),
    NON_FREE_ADD("NonFreeAdd", R.string.promotes_non_free_software),
    NON_FREE_ASSETS("NonFreeAssets", R.string.contains_non_free_media),
    NON_FREE_DEP("NonFreeDep", R.string.has_non_free_dependencies),
    NON_FREE_NET("NonFreeNet", R.string.promotes_non_free_network_services),
    TRACKING("Tracking", R.string.tracks_or_reports_your_activity),
    NON_FREE_UPSTREAM("UpstreamNonFree", R.string.upstream_source_code_is_not_free),
    NSFW("NSFW", R.string.not_safe_for_work)
}

sealed interface ComponentState {
    val icon: ImageVector
    val textId: Int
}

sealed class DownloadState(
    @StringRes override val textId: Int,
    override val icon: ImageVector = Phosphor.X
) : ComponentState {

    object Pending : DownloadState(R.string.pending)
    object Connecting : DownloadState(R.string.connecting)
    class Downloading(val downloaded: Long, val total: Long?) :
        DownloadState(R.string.downloading)

    object Installing : DownloadState(R.string.installing)
}

sealed class ActionState(
    @StringRes override val textId: Int,
    override val icon: ImageVector = Phosphor.Download
) : ComponentState {

    object Install : ActionState(R.string.install, Phosphor.Download)
    object Update : ActionState(R.string.update, Phosphor.Download)
    object Uninstall : ActionState(R.string.uninstall, Phosphor.TrashSimple)
    object Launch : ActionState(R.string.launch, Phosphor.ArrowSquareOut)
    object Details : ActionState(R.string.details, Phosphor.SlidersHorizontal)
    object Share : ActionState(R.string.share, Phosphor.ShareNetwork)
    class Cancel(@StringRes stateId: Int) : ActionState(stateId, Phosphor.X)
    object NoAction : ActionState(R.string.no_action_possible, Phosphor.X)
    object Bookmark : ActionState(R.string.favorite_add, Phosphor.HeartStraight)
    object Bookmarked : ActionState(R.string.favorite_remove, Phosphor.HeartStraightFill)
}

open class LinkType(
    @DrawableRes val iconResId: Int,
    val title: String,
    val link: Uri? = null
)

class DonateType(donate: Donate, context: Context) : LinkType(
    iconResId = when (donate) {
        is Donate.Regular -> R.drawable.ic_donate_regular
        is Donate.Bitcoin -> R.drawable.ic_donate_bitcoin
        is Donate.Litecoin -> R.drawable.ic_donate_litecoin
        is Donate.Flattr -> R.drawable.ic_donate_flattr
        is Donate.Liberapay -> R.drawable.ic_donate_liberapay
        is Donate.OpenCollective -> R.drawable.ic_donate_opencollective
    },
    title = when (donate) {
        is Donate.Regular -> context.getString(R.string.website)
        is Donate.Bitcoin -> "Bitcoin"
        is Donate.Litecoin -> "Litecoin"
        is Donate.Flattr -> "Flattr"
        is Donate.Liberapay -> "Liberapay"
        is Donate.OpenCollective -> "Open Collective"
    },
    link = when (donate) {
        is Donate.Regular -> Uri.parse(donate.url)
        is Donate.Bitcoin -> Uri.parse("bitcoin:${donate.address}")
        is Donate.Litecoin -> Uri.parse("litecoin:${donate.address}")
        is Donate.Flattr -> Uri.parse("https://flattr.com/thing/${donate.id}")
        is Donate.Liberapay -> Uri.parse("https://liberapay.com/~${donate.id}")
        is Donate.OpenCollective -> Uri.parse("https://opencollective.com/${donate.id}")
    }
)

sealed class Request {
    internal abstract val id: Int
    internal abstract val installed: Boolean
    internal abstract val updates: Boolean
    internal abstract val searchQuery: String
    internal abstract val filteredOutRepos: Set<String>
    internal abstract val filteredOutCategories: Set<String>
    internal abstract val section: Section
    internal abstract val order: Order
    internal abstract val ascending: Boolean
    internal abstract val updateCategory: UpdateCategory
    internal open val numberOfItems: Int = 0

    data class ProductsAll(
        override val searchQuery: String,
        override val section: Section,
    ) : Request() {
        override val id: Int
            get() = 1
        override val installed: Boolean
            get() = false
        override val updates: Boolean
            get() = false
        override val updateCategory: UpdateCategory
            get() = UpdateCategory.ALL
        override val order: Order
            get() = Preferences[Preferences.Key.SortOrderExplore].order
        override val filteredOutRepos: Set<String>
            get() = Preferences[Preferences.Key.ReposFilterExplore]
        override val filteredOutCategories: Set<String>
            get() = Preferences[Preferences.Key.CategoriesFilterExplore]
        override val ascending: Boolean
            get() = Preferences[Preferences.Key.SortOrderAscendingExplore]
    }

    data class ProductsInstalled(
        override val searchQuery: String,
        override val section: Section,
    ) : Request() {
        override val id: Int
            get() = 2
        override val installed: Boolean
            get() = true
        override val updates: Boolean
            get() = false
        override val updateCategory: UpdateCategory
            get() = UpdateCategory.ALL
        override val order: Order
            get() = Preferences[Preferences.Key.SortOrderInstalled].order
        override val filteredOutRepos: Set<String>
            get() = Preferences[Preferences.Key.ReposFilterInstalled]
        override val filteredOutCategories: Set<String>
            get() = Preferences[Preferences.Key.CategoriesFilterInstalled]
        override val ascending: Boolean
            get() = Preferences[Preferences.Key.SortOrderAscendingInstalled]
    }

    data class ProductsUpdates(
        override val searchQuery: String,
        override val section: Section,
    ) : Request() {
        override val id: Int
            get() = 3
        override val installed: Boolean
            get() = true
        override val updates: Boolean
            get() = true
        override val updateCategory: UpdateCategory
            get() = UpdateCategory.ALL
        override val filteredOutRepos: Set<String>
            get() = emptySet()
        override val filteredOutCategories: Set<String>
            get() = emptySet()
        override val order: Order
            get() = Order.NAME
        override val ascending: Boolean
            get() = true
    }

    data class ProductsUpdated(
        override val searchQuery: String, override val section: Section,
    ) : Request() {
        override val id: Int
            get() = 4
        override val installed: Boolean
            get() = false
        override val updates: Boolean
            get() = false
        override val updateCategory: UpdateCategory
            get() = UpdateCategory.UPDATED
        override val filteredOutRepos: Set<String>
            get() = Preferences[Preferences.Key.ReposFilterLatest]
        override val filteredOutCategories: Set<String>
            get() = Preferences[Preferences.Key.CategoriesFilterLatest]
        override val order: Order
            get() = Preferences[Preferences.Key.SortOrderLatest].order
        override val ascending: Boolean
            get() = Preferences[Preferences.Key.SortOrderAscendingLatest]
        override val numberOfItems: Int
            get() = Preferences[Preferences.Key.UpdatedApps]
    }

    data class ProductsNew(
        override val searchQuery: String,
        override val section: Section,
    ) : Request() {
        override val id: Int
            get() = 5
        override val installed: Boolean
            get() = false
        override val updates: Boolean
            get() = false
        override val updateCategory: UpdateCategory
            get() = UpdateCategory.NEW
        override val filteredOutRepos: Set<String>
            get() = emptySet()
        override val filteredOutCategories: Set<String>
            get() = emptySet()
        override val order: Order
            get() = Order.DATE_ADDED
        override val ascending: Boolean
            get() = false
        override val numberOfItems: Int
            get() = Preferences[Preferences.Key.NewApps]
    }
}

class PermissionsType(
    val group: PermissionGroupInfo?,
    val permissions: List<PermissionInfo>,
)
