package com.wayfinder.android.feature.strategy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.wayfinder.android.data.repository.StrategyRepository
import com.wayfinder.android.data.remote.ActionDTO
import com.wayfinder.android.data.remote.BlockerDTO
import com.wayfinder.android.data.remote.ExplanationDTO
import com.wayfinder.android.data.remote.StrategyDTO
import com.wayfinder.android.data.remote.TrajectoryDTO

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StrategyScreen(
    onViewOutcome: (String) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: StrategyViewModel = viewModel {
        val app = WayfinderApp.get()
        StrategyViewModel(StrategyRepository(app.api))
    }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLoggedOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.strategy_title)) },
                actions = {
                    OutlinedButton(
                        onClick = onLoggedOut,
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Text(stringResource(R.string.action_logout))
                    }
                }
            )
        }
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
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding)
            )
            state.strategy != null -> StrategyContent(
                strategy = state.strategy!!,
                explanation = state.explanation,
                onViewOutcome = onViewOutcome,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("retry_button")
        ) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun StrategyContent(
    strategy: StrategyDTO,
    explanation: ExplanationDTO?,
    onViewOutcome: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = strategy.title ?: "Strategy",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("strategy_title")
            )
            strategy.summary?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        strategy.bestTrajectory?.let { traj ->
            item {
                SectionCard(
                    title = stringResource(R.string.strategy_best_trajectory),
                    testTag = "best_trajectory_section"
                ) {
                    TrajectoryItem(traj)
                }
            }
        }

        if (strategy.blockers.isNotEmpty()) {
            item {
                SectionCard(title = stringResource(R.string.strategy_blockers)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        strategy.blockers.forEach { BlockerItem(it) }
                    }
                }
            }
        }

        if (strategy.actions.isNotEmpty()) {
            item {
                SectionCard(title = stringResource(R.string.strategy_actions)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        strategy.actions.forEach { ActionItem(it) }
                    }
                }
            }
        }

        // Explanation paths — server-authoritative, rendered verbatim.
        // The client never derives key factors or assumptions.
        if (explanation != null) {
            item {
                ExplanationCard(explanation)
            }
        }

        item {
            Button(
                onClick = { onViewOutcome(strategy.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_outcome_button")
            ) {
                Text(stringResource(R.string.outcome_title))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { m -> if (testTag != null) m.testTag(testTag) else m },
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

/**
 * Renders the server-provided explanation: a summary, key factors, and
 * assumptions. All content is opaque to the client — it is never parsed,
 * ranked, or transformed.
 */
@Composable
private fun ExplanationCard(explanation: ExplanationDTO) {
    SectionCard(
        title = stringResource(R.string.strategy_explanation),
        testTag = "explanation_section"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            explanation.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (explanation.keyFactors.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.explanation_key_factors),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                explanation.keyFactors.forEach { factor ->
                    BulletRow(text = factor)
                }
            }
            if (explanation.assumptions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.explanation_assumptions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                explanation.assumptions.forEach { assumption ->
                    BulletRow(text = assumption)
                }
            }
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrajectoryItem(traj: TrajectoryDTO) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        traj.label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        traj.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!traj.confidenceLabel.isNullOrBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = {
                    // Rendered verbatim — server-authoritative label, never computed.
                    Text("confidence: ${traj.confidenceLabel}")
                })
            }
        }
    }
}

@Composable
private fun BlockerItem(blocker: BlockerDTO) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = blocker.label ?: "Blocker",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        blocker.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionItem(action: ActionDTO) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = action.title ?: "Action",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        action.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
