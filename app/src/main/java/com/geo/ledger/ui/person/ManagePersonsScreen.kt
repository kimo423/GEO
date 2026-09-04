package com.geo.ledger.ui.person

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
fun ManagePersonsScreen(
    viewModel: ManagePersonsViewModel = composeViewModel(
        factory = GeoViewModelFactory(
            (LocalContext.current.applicationContext as GeoApplication).repository,
        ),
    ),
) {
    val persons by viewModel.activePersons.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    ManageOptionsContent(
        items = persons.map { it.id to it.name },
        isBusy = isBusy,
        events = viewModel.events,
        emptyText = stringResource(R.string.empty_persons),
        addLabel = stringResource(R.string.add_person),
        addDialogTitle = stringResource(R.string.add_person_title),
        editDialogTitle = stringResource(R.string.edit_person),
        nameLabel = stringResource(R.string.person_name),
        deleteTitleRes = R.string.delete_person_title,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}
