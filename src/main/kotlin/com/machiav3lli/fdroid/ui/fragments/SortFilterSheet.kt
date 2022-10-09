package com.machiav3lli.fdroid.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment
import com.machiav3lli.fdroid.EXTRA_PAGE_ROUTE
import com.machiav3lli.fdroid.MainApplication
import com.machiav3lli.fdroid.R
import com.machiav3lli.fdroid.content.Preferences
import com.machiav3lli.fdroid.index.RepositoryUpdater.db
import com.machiav3lli.fdroid.ui.compose.components.ActionButton
import com.machiav3lli.fdroid.ui.compose.components.ChipsSwitch
import com.machiav3lli.fdroid.ui.compose.components.SelectChip
import com.machiav3lli.fdroid.ui.compose.icons.Phosphor
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.ArrowUUpLeft
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.Check
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.SortAscending
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.SortDescending
import com.machiav3lli.fdroid.ui.compose.theme.AppTheme
import com.machiav3lli.fdroid.ui.navigation.NavItem
import com.machiav3lli.fdroid.utility.isDarkTheme

class SortFilterSheet() : FullscreenBottomSheetDialogFragment() {

    constructor(pageRoute: String = NavItem.Explore.destination) : this() {
        arguments = Bundle().apply {
            putString(EXTRA_PAGE_ROUTE, pageRoute)
        }
    }

    private val pageRoute: String
        get() = requireArguments().getString(EXTRA_PAGE_ROUTE, NavItem.Explore.destination)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme(
                    darkTheme = when (Preferences[Preferences.Key.Theme]) {
                        is Preferences.Theme.System -> isSystemInDarkTheme()
                        is Preferences.Theme.SystemBlack -> isSystemInDarkTheme()
                        else -> isDarkTheme
                    }
                ) {
                    SortFilterPage(pageRoute)
                }
            }
        }
    }

    override fun setupLayout() {
    }

    override fun updateSheet() {
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    fun SortFilterPage(navPage: String) {
        val nestedScrollConnection = rememberNestedScrollInteropConnection()
        val dbHandler = ((context as AppCompatActivity).application as MainApplication).db
        val repos by dbHandler.repositoryDao.allLive.observeAsState(emptyList())
        val categories by db.categoryDao.allNamesLive.observeAsState(emptyList())
        val activeRepos by remember(repos) { mutableStateOf(repos.filter { it.enabled }) }

        val sortKey = when (navPage) {
            NavItem.Latest.destination -> Preferences.Key.SortOrderLatest
            NavItem.Installed.destination -> Preferences.Key.SortOrderInstalled
            else -> Preferences.Key.SortOrderExplore // NavItem.Explore
        }
        val sortAscendingKey = when (navPage) {
            NavItem.Latest.destination -> Preferences.Key.SortOrderAscendingLatest
            NavItem.Installed.destination -> Preferences.Key.SortOrderAscendingInstalled
            else -> Preferences.Key.SortOrderAscendingExplore // NavItem.Explore
        }
        val reposFilterKey = when (navPage) {
            NavItem.Latest.destination -> Preferences.Key.ReposFilterLatest
            NavItem.Installed.destination -> Preferences.Key.ReposFilterInstalled
            else -> Preferences.Key.ReposFilterExplore // NavItem.Explore
        }
        val categoriesFilterKey = when (navPage) {
            NavItem.Latest.destination -> Preferences.Key.CategoriesFilterLatest
            NavItem.Installed.destination -> Preferences.Key.CategoriesFilterInstalled
            else -> Preferences.Key.CategoriesFilterExplore // NavItem.Explore
        }

        var sortOption by remember(Preferences[sortKey]) {
            mutableStateOf(Preferences[sortKey])
        }
        var sortAscending by remember(Preferences[sortAscendingKey]) {
            mutableStateOf(Preferences[sortAscendingKey])
        }
        val filteredOutRepos by remember(Preferences[reposFilterKey]) {
            mutableStateOf(Preferences[reposFilterKey].toMutableSet())
        }
        val filteredOutCategories by remember(Preferences[categoriesFilterKey]) {
            mutableStateOf(Preferences[categoriesFilterKey].toMutableSet())
        }

        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.action_reset),
                        icon = Phosphor.ArrowUUpLeft,
                        positive = false,
                        onClick = ::dismissAllowingStateLoss
                    )
                    ActionButton(
                        text = stringResource(id = R.string.action_apply),
                        icon = Phosphor.Check,
                        modifier = Modifier.weight(1f),
                        positive = true,
                        onClick = {
                            // TODO save prefs
                            Preferences[sortKey] = sortOption
                            Preferences[sortAscendingKey] = sortAscending
                            Preferences[reposFilterKey] = filteredOutRepos
                            Preferences[categoriesFilterKey] = filteredOutCategories
                            dismissAllowingStateLoss()
                        }
                    )
                }
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .nestedScroll(nestedScrollConnection)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                item {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.sorting_order),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 4.dp,
                        mainAxisAlignment = MainAxisAlignment.Center,
                    ) {

                        sortKey.default.value.values.forEach {
                            SelectChip(
                                text = stringResource(id = it.order.titleResId),
                                checked = it == sortOption
                            ) {
                                sortOption = it
                            }
                        }
                    }

                    ChipsSwitch(
                        firstTextId = R.string.sort_ascending,
                        firstIcon = Phosphor.SortAscending,
                        secondTextId = R.string.sort_descending,
                        secondIcon = Phosphor.SortDescending,
                        firstSelected = sortAscending,
                        onCheckedChange = { checked ->
                            sortAscending = checked
                        }
                    )
                }
                item {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.repositories),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 4.dp,
                        mainAxisAlignment = MainAxisAlignment.Center,
                    ) {
                        activeRepos.forEach {
                            var checked by remember {
                                mutableStateOf(
                                    !filteredOutRepos.contains(it.id.toString())
                                )
                            }

                            SelectChip(
                                text = it.name,
                                checked = checked
                            ) {
                                checked = !checked
                                if (checked)
                                    filteredOutRepos.remove(it.id.toString())
                                else
                                    filteredOutRepos.add(it.id.toString())
                            }
                        }
                    }
                }
                item {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.categories),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 4.dp,
                        mainAxisAlignment = MainAxisAlignment.Center,
                    ) {
                        categories.sorted().forEach {
                            var checked by remember {
                                mutableStateOf(
                                    !filteredOutCategories.contains(it)
                                )
                            }

                            SelectChip(
                                text = it,
                                checked = checked
                            ) {
                                checked = !checked
                                if (checked)
                                    filteredOutCategories.remove(it)
                                else
                                    filteredOutCategories.add(it)
                            }
                        }
                    }
                }
            }
        }
    }
}
