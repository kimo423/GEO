package com.geo.ledger.ui.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geo.ledger.R
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.domain.OptionNameValidator
import kotlinx.coroutines.flow.Flow

private sealed interface EditorDialog {
    data object Add : EditorDialog
    data class Edit(val id: Long, val currentName: String) : EditorDialog
}

@Composable
internal fun ManageOptionsContent(
    items: List<Pair<Long, String>>,
    isBusy: Boolean,
    events: Flow<ManageOptionEvent>,
    emptyText: String,
    addLabel: String,
    addDialogTitle: String,
    editDialogTitle: String,
    nameLabel: String,
    deleteTitleRes: Int,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var editor by remember { mutableStateOf<EditorDialog?>(null) }
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var editorName by remember { mutableStateOf("") }
    var attemptedSave by remember { mutableStateOf(false) }
    var editorErrorRes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                ManageOptionEvent.Saved -> {
                    editor = null
                    pendingDelete = null
                    editorName = ""
                    attemptedSave = false
                    editorErrorRes = null
                }
                is ManageOptionEvent.Failed -> {
                    editorErrorRes = event.messageRes
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    itemsIndexed(items, key = { _, item -> item.first }) { index, (id, name) ->
                        if (index > 0) HorizontalDivider()
                        OptionRow(
                            name = name,
                            enabled = !isBusy,
                            onEdit = {
                                editor = EditorDialog.Edit(id, name)
                                editorName = name
                                attemptedSave = false
                                editorErrorRes = null
                            },
                            onDelete = { pendingDelete = id to name },
                        )
                    }
                }
            }
            Button(
                onClick = {
                    editor = EditorDialog.Add
                    editorName = ""
                    attemptedSave = false
                    editorErrorRes = null
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text(addLabel)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
        )
    }

    val currentEditor = editor
    if (currentEditor != null) {
        val excludeId = (currentEditor as? EditorDialog.Edit)?.id
        val preview = OptionNameValidator.validate(
            raw = editorName,
            activeItems = items,
            excludeId = excludeId,
            maxLength = LedgerRepository.MAX_OPTION_NAME_LENGTH,
        )
        val liveError = when {
            preview is OptionNameValidator.Result.Invalid &&
                (attemptedSave || preview.reason != OptionNameValidator.Reason.Blank) ->
                preview.reason.toMessageRes()
            else -> editorErrorRes
        }
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    editor = null
                    editorErrorRes = null
                }
            },
            title = {
                Text(
                    text = if (currentEditor is EditorDialog.Add) addDialogTitle else editDialogTitle,
                )
            },
            text = {
                OutlinedTextField(
                    value = editorName,
                    onValueChange = {
                        editorName = it
                        editorErrorRes = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(nameLabel) },
                    isError = liveError != null,
                    supportingText = liveError?.let { res ->
                        { Text(stringResource(res)) }
                    },
                    enabled = !isBusy,
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        attemptedSave = true
                        when (currentEditor) {
                            EditorDialog.Add -> onAdd(editorName)
                            is EditorDialog.Edit -> onRename(currentEditor.id, editorName)
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isBusy) {
                            editor = null
                            editorErrorRes = null
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { if (!isBusy) pendingDelete = null },
            title = { Text(stringResource(deleteTitleRes, name)) },
            text = { Text(stringResource(R.string.delete_option_message)) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(id) },
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isBusy) pendingDelete = null },
                    enabled = !isBusy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun OptionRow(
    name: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.edit_named, name),
            )
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete_named, name),
            )
        }
    }
}
