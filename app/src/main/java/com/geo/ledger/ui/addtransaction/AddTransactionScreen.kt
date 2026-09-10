package com.geo.ledger.ui.addtransaction

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.geo.ledger.GeoApplication
import com.geo.ledger.R
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.NavigationLockPolicy
import com.geo.ledger.ui.GeoViewModelFactory
import com.geo.ledger.ui.theme.GeoCard
import com.geo.ledger.ui.theme.GeoExpense
import com.geo.ledger.ui.theme.GeoIncome
import com.geo.ledger.util.GeoDates
import com.geo.ledger.util.MoneyFormatter
import com.geo.ledger.util.MoneyParser
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.geo.ledger.ui.attachments.AttachmentList
import com.geo.ledger.data.transfer.AttachmentRef

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    onSaved: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigationLock: (Boolean) -> Unit = {},
    viewModel: AddTransactionViewModel = composeViewModel(
        factory = GeoViewModelFactory(
            (LocalContext.current.applicationContext as GeoApplication).repository,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val attachmentBusy by viewModel.attachmentBusy.collectAsStateWithLifecycle()
    val attachmentLoadFailed by viewModel.attachmentLoadFailed.collectAsStateWithLifecycle()
    val attachmentMessage by viewModel.attachmentMessage.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val pickAttachments=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.addAttachments(context,it) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showDatePicker by remember { mutableStateOf(false) }
    val completionConsumed = remember { AtomicBoolean(false) }
    BackHandler(enabled = uiState.isSaving || attachmentBusy) { }
    DisposableEffect(uiState.isSaving,attachmentBusy) {
        onNavigationLock(uiState.isSaving || attachmentBusy)
        onDispose { onNavigationLock(false) }
    }
    LaunchedEffect(attachmentMessage) {
        attachmentMessage?.let { snackbarHostState.showSnackbar(it); viewModel.attachmentMessage.value=null }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddTransactionEvent.Saved -> {
                    if (NavigationLockPolicy.allowCompletionBack(completionConsumed.get()) &&
                        completionConsumed.compareAndSet(false, true)
                    ) {
                        onSaved(event.messageRes)
                    }
                }
                is AddTransactionEvent.Failed -> {
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                    if (event.messageRes == R.string.transaction_missing) onBack()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        if (!uiState.isReady) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = com.geo.ledger.ui.theme.GeoSpacing.Page, vertical = com.geo.ledger.ui.theme.GeoSpacing.Section),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TypeSelector(
                        type = uiState.type,
                        onTypeSelected = viewModel::setType,
                    )
                    AmountField(
                        type = uiState.type,
                        amountRaw = uiState.amountRaw,
                        amountIssue = uiState.amountIssue,
                        onAmountChange = viewModel::onAmountChange,
                    )
                    if (uiState.type == TransactionType.EXPENSE) {
                        ChipSection(
                            title = "使用人（多选） · 已选 ${uiState.people.size} 人",
                            emptyText = stringResource(R.string.empty_persons),
                            chips = uiState.personChips,
                            onToggle = viewModel::togglePerson,
                            onSelectAll = viewModel::selectAllPersons,
                            onClear = viewModel::clearPersons,
                        )
                        ChipSection(
                            title = stringResource(R.string.category),
                            emptyText = stringResource(R.string.empty_categories),
                            chips = uiState.categoryChips,
                            onToggle = viewModel::toggleCategory,
                        )
                    } else {
                        OutlinedTextField(
                            value = uiState.source,
                            onValueChange = viewModel::onSourceChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.source)) },
                            placeholder = { Text(stringResource(R.string.source_placeholder)) },
                            isError = uiState.sourceError,
                            supportingText = if (uiState.sourceError) {
                                { Text(stringResource(R.string.error_text_too_long)) }
                            } else {
                                null
                            },
                            singleLine = true,
                        )
                    }
                    DateField(
                        epochDay = uiState.epochDay,
                        onClick = { showDatePicker = true },
                    )
                    OutlinedTextField(
                        value = uiState.note,
                        onValueChange = viewModel::onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.note)) },
                        placeholder = { Text(stringResource(R.string.note_placeholder)) },
                        isError = uiState.noteError,
                        supportingText = if (uiState.noteError) {
                            { Text(stringResource(R.string.error_text_too_long)) }
                        } else {
                            null
                        },
                        minLines = 3,
                        maxLines = 6,
                    )
                    Text("附件（可选） · ${attachments.size}/10",style=MaterialTheme.typography.titleSmall)
                    Text("${com.geo.ledger.ui.attachments.sizeLabel(attachments.sumOf { it.sizeBytes })} / 30 MiB · 单个最多 10 MiB",style=MaterialTheme.typography.bodySmall)
                    TextButton(enabled=!attachmentBusy && !uiState.isSaving,onClick={pickAttachments.launch(arrayOf("*/*"))}) { Text("＋ 添加附件") }
                    if(attachmentBusy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(attachmentLoadFailed) Text("附件加载失败，已禁止保存。请返回后重试。",color=MaterialTheme.colorScheme.error)
                    AttachmentList(attachments.map(AttachmentRef::from),attachments.associate { it.relation.attachmentUuid to it.internalStorageKey },
                        enabled=!attachmentBusy && !uiState.isSaving,onRemove=viewModel::removeAttachment)
                }
                HorizontalDivider()
                SaveButton(
                    state = uiState.copy(canSave=uiState.canSave && !attachmentBusy && !attachmentLoadFailed),
                    onSave = viewModel::save,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = GeoDates.fromEpochDayOrNull(uiState.epochDay)
                ?.let(GeoDates::toPickerMillisOrNull),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            GeoDates.fromPickerMillisOrNull(millis)?.let(viewModel::setDate)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TypeSelector(
    type: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
) {
    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, value ->
            SegmentedButton(
                selected = type == value,
                onClick = { onTypeSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                label = {
                    Text(
                        text = stringResource(
                            if (value == TransactionType.EXPENSE) R.string.expense else R.string.income,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun AmountField(
    type: TransactionType,
    amountRaw: String,
    amountIssue: MoneyParser.DraftIssue,
    onAmountChange: (String) -> Unit,
) {
    val amountColor = if (type == TransactionType.EXPENSE) GeoExpense else GeoIncome
    val errorText = when (amountIssue) {
        MoneyParser.DraftIssue.TooLong -> stringResource(R.string.error_amount_too_long)
        MoneyParser.DraftIssue.Invalid -> stringResource(R.string.error_amount_invalid)
        else -> null
    }
    OutlinedTextField(
        value = amountRaw,
        onValueChange = onAmountChange,
        modifier = Modifier.fillMaxWidth().then(if (com.geo.ledger.ui.theme.isGraphite)
            Modifier.clip(RoundedCornerShape(24.dp)).background(com.geo.ledger.ui.theme.GraphiteInk).padding(16.dp) else Modifier),
        colors = if (com.geo.ledger.ui.theme.isGraphite) OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = Color(0xFFCAD2DC), unfocusedLabelColor = Color(0xFFCAD2DC),
            focusedPrefixColor = Color.White, unfocusedPrefixColor = Color.White,
            focusedPlaceholderColor = Color(0xFFCAD2DC), unfocusedPlaceholderColor = Color(0xFFCAD2DC),
            errorLabelColor = Color(0xFFFFB8AC), errorSupportingTextColor = Color(0xFFFFB8AC),
            errorBorderColor = Color(0xFFFFB8AC), cursorColor = Color.White,
        ) else OutlinedTextFieldDefaults.colors(),
        textStyle = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = if (com.geo.ledger.ui.theme.isGraphite) Color.White else amountColor,
        ),
        label = {
            Text(
                stringResource(
                    if (type == TransactionType.EXPENSE) R.string.expense_amount else R.string.income_amount,
                ),
            )
        },
        placeholder = { Text(stringResource(R.string.amount_optional_hint)) },
        prefix = { Text(stringResource(R.string.currency_symbol)) },
        isError = errorText != null,
        supportingText = errorText?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(
    title: String,
    emptyText: String,
    chips: List<com.geo.ledger.domain.OptionChipModel>,
    onToggle: (com.geo.ledger.domain.OptionChipModel) -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    Column(modifier = if (com.geo.ledger.ui.theme.isGraphite) Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp) else Modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        if (onSelectAll != null && onClear != null) {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onSelectAll, enabled = chips.any { !it.historical && !it.selected }) { Text("全选使用人") }
                TextButton(onClick = onClear, enabled = chips.any { it.selected }) { Text("清空使用人") }
            }
        }
        if (chips.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { chip ->
                    FilterChip(
                        selected = chip.selected,
                        onClick = { onToggle(chip) },
                        leadingIcon = if (onSelectAll != null && chip.selected) {
                            { androidx.compose.material3.Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.widthIn(max = 280.dp),
                        label = {
                            Text(
                                text = if (chip.historical) {
                                    stringResource(R.string.historical_option) + " · " + chip.label
                                } else {
                                    chip.label
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DateField(
    epochDay: Long,
    onClick: () -> Unit,
) {
    val pickDate = stringResource(R.string.pick_date)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = GeoCard,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = pickDate },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = GeoDates.formatEpochDay(epochDay),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SaveButton(
    state: AddTransactionUiState,
    onSave: () -> Unit,
) {
    val parsed = MoneyParser.parseCents(state.amountRaw)
    val saveLabel = when {
        parsed != null && state.type == TransactionType.EXPENSE ->
            stringResource(R.string.save_expense, MoneyFormatter.unsigned(parsed))
        parsed != null ->
            stringResource(R.string.save_income, MoneyFormatter.unsigned(parsed))
        state.type == TransactionType.EXPENSE -> stringResource(R.string.save_expense_idle)
        else -> stringResource(R.string.save_income_idle)
    }
    Button(
        onClick = onSave,
        enabled = state.canSave,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .heightIn(min = 48.dp)
            .semantics { contentDescription = saveLabel },
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(saveLabel)
        }
    }
}
