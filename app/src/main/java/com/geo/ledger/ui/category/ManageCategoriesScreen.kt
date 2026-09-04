package com.geo.ledger.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.geo.ledger.GeoApplication
import com.geo.ledger.R
import com.geo.ledger.ui.GeoViewModelFactory
import com.geo.ledger.ui.options.ManageOptionsContent

@Composable
fun ManageCategoriesScreen(
    viewModel: ManageCategoriesViewModel = composeViewModel(
        factory = GeoViewModelFactory(
            (LocalContext.current.applicationContext as GeoApplication).repository,
        ),
    ),
) {
    val categories by viewModel.activeCategories.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    ManageOptionsContent(
        items = categories.map { it.id to it.name },
        isBusy = isBusy,
        events = viewModel.events,
        emptyText = stringResource(R.string.empty_categories),
        addLabel = stringResource(R.string.add_category),
        addDialogTitle = stringResource(R.string.add_category_title),
        editDialogTitle = stringResource(R.string.edit_category),
        nameLabel = stringResource(R.string.category_name),
        deleteTitleRes = R.string.delete_category_title,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}
