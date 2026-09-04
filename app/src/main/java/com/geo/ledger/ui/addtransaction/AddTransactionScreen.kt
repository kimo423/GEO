package com.geo.ledger.ui.addtransaction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import com.geo.ledger.ui.GeoViewModelFactory
import com.geo.ledger.ui.theme.GeoCard
import com.geo.ledger.ui.theme.GeoExpense
import com.geo.ledger.ui.theme.GeoIncome
import com.geo.ledger.util.GeoDates
import com.geo.ledger.util.MoneyFormatter
import com.geo.ledger.util.MoneyParser
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onNavigationLock: (Boolean) -> Unit = {},
    viewModel: AddTransactionViewModel = composeViewModel(
        factory = GeoViewModelFactory(
            (LocalContext.current.applicationContext as GeoApplication).repository,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showDatePicker by remember { mutableStateOf(false) }
    BackHandler(enabled = uiState.isSaving) { }
    DisposableEffect(uiState.isSaving) {
        onNavigationLock(uiState.isSaving)
        onDispose { onNavigationLock(false) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AddTransactionEvent.Saved -> onSaved()
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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
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
                            title = stringResource(R.string.person),
                            emptyText = stringResource(R.string.empty_persons),
                            chips = uiState.personChips,
                            onToggle = viewModel::togglePerson,
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
                }
                HorizontalDivider()
                SaveButton(
                    state = uiState,
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
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
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
