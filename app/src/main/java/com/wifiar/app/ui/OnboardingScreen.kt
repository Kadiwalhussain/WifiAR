package com.wifiar.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wifiar.app.R
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.ui.components.CompactPrimaryButton
import com.wifiar.app.ui.theme.NeonCyan
import com.wifiar.app.ui.theme.NeonMagenta
import com.wifiar.app.ui.theme.NeonMint
import kotlinx.coroutines.launch

private data class OnboardPage(
    val titleRes: Int,
    val bodyRes: Int,
    val glyph: String,
)

/**
 * First-launch tutorial — compact, animated, futuristic.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = listOf(
        OnboardPage(R.string.onboard_1_title, R.string.onboard_1_body, "◈"),
        OnboardPage(R.string.onboard_2_title, R.string.onboard_2_body, "◎"),
        OnboardPage(R.string.onboard_3_title, R.string.onboard_3_body, "≋"),
        OnboardPage(R.string.onboard_4_title, R.string.onboard_4_body, "✦"),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    fun finish() {
        UserPreferences.onboardingDone = true
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { finish() }) {
                    Text(
                        stringResource(R.string.onboard_skip),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        NeonCyan.copy(alpha = 0.35f),
                                        NeonMagenta.copy(alpha = 0.25f),
                                    ),
                                ),
                            )
                            .border(
                                1.dp,
                                NeonCyan.copy(alpha = 0.5f),
                                RoundedCornerShape(28.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = pages[page].glyph,
                            style = MaterialTheme.typography.displaySmall,
                            color = NeonCyan,
                        )
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = stringResource(pages[page].titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(pages[page].bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pages.size) { i ->
                    val selected = i == pager.currentPage
                    val w by animateDpAsState(
                        targetValue = if (selected) 18.dp else 7.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "dotW",
                    )
                    val c by animateColorAsState(
                        targetValue = if (selected) NeonCyan else MaterialTheme.colorScheme.outlineVariant,
                        label = "dotC",
                    )
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(width = w, height = 7.dp)
                            .clip(CircleShape)
                            .background(c),
                    )
                }
            }

            CompactPrimaryButton(
                text = if (pager.currentPage >= pages.lastIndex) {
                    stringResource(R.string.onboard_get_started)
                } else {
                    stringResource(R.string.onboard_next)
                },
                onClick = {
                    if (pager.currentPage >= pages.lastIndex) {
                        finish()
                    } else {
                        scope.launch {
                            pager.animateScrollToPage(pager.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                containerColor = NeonCyan,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "WifiAR · signal intelligence in AR",
                style = MaterialTheme.typography.labelSmall,
                color = NeonMint.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
