package com.wifiar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.Config
import com.wifiar.app.AppConfig
import com.wifiar.app.R
import com.wifiar.app.data.analysis.PathLossModel
import com.wifiar.app.data.analysis.PlacementRecommendationResult
import com.wifiar.app.data.analysis.RecommendationCache
import com.wifiar.app.data.analysis.RouterCandidate
import com.wifiar.app.data.analysis.RouterPlacementOptimizer
import com.wifiar.app.data.local.WifiArDatabase
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.SphereNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Router placement recommender UI (Part 9).
 *
 * Uses a simplified log-distance path-loss model — no wall detection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterPlacementScreen(
    sessionId: String,
    sessionLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WifiArDatabase.getInstance(context) }
    val samples by db.rssiSampleDao()
        .getAllForSession(sessionId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var result by remember { mutableStateOf<PlacementRecommendationResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showAr by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runOptimizer() {
        if (samples.size < AppConfig.ROUTER_RECOMMEND_MIN_SAMPLES) {
            error = context.getString(
                R.string.router_need_samples,
                AppConfig.ROUTER_RECOMMEND_MIN_SAMPLES,
                samples.size,
            )
            return
        }
        scope.launch {
            loading = true
            error = null
            val pathLoss = if (com.wifiar.app.data.UserPreferences.pathLossIndoor) {
                PathLossModel.indoor()
            } else {
                PathLossModel.openSpace()
            }
            val optimizer = RouterPlacementOptimizer(
                pathLoss = pathLoss,
                coverageThreshold = com.wifiar.app.data.UserPreferences.rssiMediumDbm.toFloat(),
                deadThreshold = com.wifiar.app.data.UserPreferences.rssiDeadDbm.toFloat(),
            )
            val out = withContext(Dispatchers.Default) {
                optimizer.optimize(samples)
            }
            result = out
            RecommendationCache.put(sessionId, out.candidates.firstOrNull())
            loading = false
            if (out.candidates.isEmpty()) {
                error = context.getString(R.string.router_no_candidates)
            }
        }
    }

    if (showAr && result?.candidates?.isNotEmpty() == true) {
        RouterArMarkerView(
            top = result!!.candidates.first(),
            sessionLabel = sessionLabel,
            onBack = { showAr = false },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.router_title))
                        Text(
                            text = sessionLabel,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.router_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Text(
                text = stringResource(R.string.router_sample_count, samples.size),
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = { runOptimizer() },
                enabled = !loading && samples.size >= AppConfig.ROUTER_RECOMMEND_MIN_SAMPLES,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.router_suggest))
            }

            if (samples.size < AppConfig.ROUTER_RECOMMEND_MIN_SAMPLES) {
                Text(
                    text = stringResource(
                        R.string.router_need_samples,
                        AppConfig.ROUTER_RECOMMEND_MIN_SAMPLES,
                        samples.size,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp))
                    Text(stringResource(R.string.router_computing))
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            result?.let { rec ->
                Text(
                    text = stringResource(
                        R.string.router_compute_info,
                        rec.computeTimeMs,
                        rec.pathLossExponent,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                // Current vs recommended comparison
                rec.comparison?.let { cmp ->
                    Text(
                        text = stringResource(R.string.router_comparison_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ComparisonCard(cmp)
                }

                Text(
                    text = stringResource(R.string.router_top_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                rec.candidates.forEach { cand ->
                    CandidateCard(cand)
                }

                if (rec.candidates.isNotEmpty()) {
                    Button(
                        onClick = { showAr = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.router_show_ar))
                    }
                }

                Text(
                    text = rec.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(cmp: com.wifiar.app.data.analysis.PlacementComparison) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                ComparisonColumn(
                    title = stringResource(R.string.router_current),
                    cover = cmp.currentCoverageFraction,
                    dead = cmp.currentDeadZoneFraction,
                    modifier = Modifier.weight(1f),
                )
                ComparisonColumn(
                    title = stringResource(R.string.router_recommended),
                    cover = cmp.recommendedCoverageFraction,
                    dead = cmp.recommendedDeadZoneFraction,
                    modifier = Modifier.weight(1f),
                )
            }
            val improvePct = cmp.coverageImprovementFraction * 100f
            Text(
                text = if (improvePct >= 0f) {
                    stringResource(R.string.router_improvement_up, improvePct)
                } else {
                    stringResource(R.string.router_improvement_down, improvePct)
                },
                fontWeight = FontWeight.Bold,
                color = if (improvePct >= 0f) Color(0xFF2E7D32) else Color(0xFFC62828),
            )
        }
    }
}

@Composable
private fun ComparisonColumn(
    title: String,
    cover: Float,
    dead: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.router_stat_cover, cover * 100f))
        Text(stringResource(R.string.router_stat_dead, dead * 100f))
    }
}

@Composable
private fun CandidateCard(cand: RouterCandidate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (cand.rank == 1) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.router_rank, cand.rank),
                fontWeight = FontWeight.Bold,
                color = if (cand.rank == 1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = stringResource(
                    R.string.router_pose,
                    cand.position.x,
                    cand.position.y,
                    cand.position.z,
                ),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.router_candidate_stats,
                    cand.predictedCoverageFraction * 100f,
                    cand.predictedDeadZoneFraction * 100f,
                    cand.predictedAverageRssiDbm,
                    cand.score,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RouterArMarkerView(
    top: RouterCandidate,
    sessionLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
        ) {
            val loader = materialLoader
            val glow = remember(loader) {
                runCatching {
                    loader.createUnlitColorInstance(Color(0xFFFFD54F))
                }.getOrElse {
                    loader.createColorInstance(
                        color = Color(0xFFFFD54F),
                        metallic = 0.2f,
                        roughness = 0.3f,
                    )
                }
            }
            val core = remember(loader) {
                runCatching {
                    loader.createUnlitColorInstance(Color(0xFFFF6F00))
                }.getOrElse {
                    loader.createColorInstance(
                        color = Color(0xFFFF6F00),
                        metallic = 0.1f,
                        roughness = 0.4f,
                    )
                }
            }
            key("router-rec") {
                // Outer "glow" sphere
                SphereNode(
                    radius = AppConfig.ROUTER_MARKER_RADIUS_M * 1.35f,
                    position = Position(
                        x = top.position.x,
                        y = top.position.y,
                        z = top.position.z,
                    ),
                    materialInstance = glow,
                )
                // Inner router core
                SphereNode(
                    radius = AppConfig.ROUTER_MARKER_RADIUS_M,
                    position = Position(
                        x = top.position.x,
                        y = top.position.y,
                        z = top.position.z,
                    ),
                    materialInstance = core,
                )
                TextNode(
                    text = stringResource(R.string.router_ar_label),
                    fontSize = 36f,
                    textColor = android.graphics.Color.BLACK,
                    backgroundColor = 0xCCFFD54F.toInt(),
                    widthMeters = 0.7f,
                    heightMeters = 0.28f,
                    position = Position(
                        x = top.position.x,
                        y = top.position.y + 0.35f,
                        z = top.position.z,
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .background(Color(0xBB000000), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(
                        text = stringResource(R.string.router_ar_title),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = sessionLabel,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.router_pose,
                    top.position.x,
                    top.position.y,
                    top.position.z,
                ),
                color = Color(0xFFFFD54F),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.router_ar_hint,
                    top.predictedCoverageFraction * 100f,
                ),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
            )
        }
    }
}
