package com.wayfinder.android.feature.outcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wayfinder.android.R
import com.wayfinder.android.core.WayfinderApp
import com.wayfinder.android.data.remote.OutcomeDTO
import com.wayfinder.android.data.remote.OutcomeSummaryDTO
import com.wayfinder.android.data.remote.OutcomesDTO
import com.wayfinder.android.data.repository.StrategyRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutcomeScreen(
    strategyId: String,
    onBack: () -> Unit,
    viewModel: OutcomeViewModel = viewModel {
        val app = WayfinderApp.get()
        OutcomeViewModel(StrategyRepository(app.api), strategyId)
    }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val draftValues = remember(strategyId) { mutableStateMapOf<String, String>() }

    LaunchedEffect(state.lastSubmittedType) {
        state.lastSubmittedType?.let { type ->
            draftValues.remove(type)
            viewModel.consumeLastSubmitted()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.outcome_title)) },
                navigationIcon = {
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            state.error != null && state.outcomes == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::load) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            state.outcomes != null -> OutcomeContent(
                outcomes = state.outcomes!!,
                draftValues = draftValues,
                submittingForType = state.submittingForType,
                onSubmit = viewModel::submitObservation,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun OutcomeContent(
    outcomes: OutcomesDTO,
    draftValues: MutableMap<String, String>,
    submittingForType: String?,
    onSubmit: (OutcomeDTO, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Strategy ${outcomes.strategyId}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("strategy_id_label")
            )
        }

        // Summary stats — entirely server-authoritative. The counts below are
        // rendered verbatim from [OutcomesDTO.summary]; the client NEVER
        // derives achieved/partial/missed/pending from outcome lists.
        item {
            SummaryCard(
                expectedCount = outcomes.expected.size,
                observedCount = outcomes.observed.size,
                evaluationStatus = outcomes.evaluation?.status,
                summary = outcomes.summary
            )
        }

        item {
            OutcomeSection(
                title = stringResource(R.string.outcome_expected),
                testTag = "expected_section"
            ) {
                if (outcomes.expected.isEmpty()) {
                    EmptyHint(stringResource(R.string.outcome_expected_empty))
                } else {
                    outcomes.expected.forEach { OutcomeRow(it) }
                }
            }
        }

        item {
            OutcomeSection(
                title = stringResource(R.string.outcome_observed),
                testTag = "observed_section"
            ) {
                if (outcomes.observed.isEmpty()) {
                    EmptyHint(stringResource(R.string.outcome_observed_empty))
                } else {
                    outcomes.observed.forEach { OutcomeRow(it) }
                }
            }
        }

        outcomes.evaluation?.let { eval ->
            item {
                OutcomeSection(
                    title = stringResource(R.string.outcome_evaluation),
                    testTag = "evaluation_section"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // status is server-authoritative — rendered as opaque text.
                        Text(
                            text = "Status: ${eval.status ?: "—"}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("evaluation_status")
                        )
                        eval.notes?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (outcomes.expected.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.outcome_record_observation),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(outcomes.expected, key = { it.type ?: it.id ?: it.label ?: "" }) { expected ->
                val draftKey = expected.type ?: expected.id ?: expected.label ?: ""
                ObservationForm(
                    expected = expected,
                    draft = draftValues[draftKey] ?: "",
                    onDraftChange = { v ->
                        draftValues[draftKey] = v
                    },
                    isSubmitting = submittingForType == expected.type,
                    onSubmit = { onSubmit(expected, draftValues[draftKey] ?: "") }
                )
            }
        }
    }
}

/**
 * Summary card showing list sizes plus any server-authoritative category
 * counts. The list sizes (expected/observed) are simple counts of server-
 * provided lists — NOT evaluations. The achieved/partial/missed/pending
 * values come straight from [OutcomeSummaryDTO] and are never computed.
 */
@Composable
private fun SummaryCard(
    expectedCount: Int,
    observedCount: Int,
    evaluationStatus: String?,
    summary: OutcomeSummaryDTO?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("summary_section"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.outcome_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryStat(
                    label = stringResource(R.string.summary_expected),
                    value = expectedCount.toString(),
                    modifier = Modifier.testTag("stat_expected")
                )
                SummaryStat(
                    label = stringResource(R.string.summary_observed),
                    value = observedCount.toString(),
                    modifier = Modifier.testTag("stat_observed")
                )
            }
            evaluationStatus?.let { status ->
                Text(
                    text = "${stringResource(R.string.summary_evaluation)}: $status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("stat_evaluation_status")
                )
            }
            // Server-authoritative category counts — only rendered when present.
            if (summary != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    summary.achieved?.let {
                        SummaryStat(
                            label = stringResource(R.string.summary_achieved),
                            value = it.toString(),
                            modifier = Modifier.testTag("stat_achieved")
                        )
                    }
                    summary.partial?.let {
                        SummaryStat(
                            label = stringResource(R.string.summary_partial),
                            value = it.toString(),
                            modifier = Modifier.testTag("stat_partial")
                        )
                    }
                    summary.missed?.let {
                        SummaryStat(
                            label = stringResource(R.string.summary_missed),
                            value = it.toString(),
                            modifier = Modifier.testTag("stat_missed")
                        )
                    }
                    summary.pending?.let {
                        SummaryStat(
                            label = stringResource(R.string.summary_pending),
                            value = it.toString(),
                            modifier = Modifier.testTag("stat_pending")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OutcomeSection(
    title: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun OutcomeRow(outcome: OutcomeDTO) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = outcome.label ?: outcome.type ?: "Outcome",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        outcome.value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ObservationForm(
    expected: OutcomeDTO,
    draft: String,
    onDraftChange: (String) -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = expected.label ?: expected.type ?: "Outcome",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text(stringResource(R.string.outcome_observed_value)) },
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = !isSubmitting && draft.isNotBlank(),
                    modifier = Modifier.testTag(
                        "submit_${expected.type ?: expected.label ?: ""}"
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.outcome_record))
                    }
                }
            }
        }
    }
}
