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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wifiar.app.R
import com.wifiar.app.data.UserPreferences
import kotlinx.coroutines.launch

private data class OnboardPage(
    val titleRes: Int,
    val bodyRes: Int,
)

/**
 * First-launch tutorial (Part 10).
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = listOf(
        OnboardPage(R.string.onboard_1_title, R.string.onboard_1_body),
        OnboardPage(R.string.onboard_2_title, R.string.onboard_2_body),
        OnboardPage(R.string.onboard_3_title, R.string.onboard_3_body),
        OnboardPage(R.string.onboard_4_title, R.string.onboard_4_body),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    fun finish() {
        UserPreferences.onboardingDone = true
        onFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { finish() }) {
                Text(stringResource(R.string.onboard_skip))
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
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${page + 1}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = stringResource(pages[page].titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(pages[page].bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (i == pager.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pager.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }

        Button(
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
        ) {
            Text(
                if (pager.currentPage >= pages.lastIndex) {
                    stringResource(R.string.onboard_get_started)
                } else {
                    stringResource(R.string.onboard_next)
                },
            )
        }
    }
}
