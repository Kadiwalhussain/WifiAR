package com.wifiar.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wifiar.app.R
import com.wifiar.app.data.auth.TokenStore
import com.wifiar.app.data.sync.AuthRepository
import com.wifiar.app.data.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * Simple login / register forms for cloud sync (Part 7).
 */
@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    onAuthenticated: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { TokenStore(context) }
    val auth = remember { AuthRepository(tokenStore = tokenStore) }
    val syncManager = remember { SyncManager(context, tokenStore = tokenStore) }

    var modeRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(tokenStore.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    if (auth.isLoggedIn) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.auth_signed_in_as, tokenStore.email.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        val n = syncManager.syncAllPending()
                        info = context.getString(R.string.auth_sync_result, n)
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.auth_sync_now))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    auth.logout()
                    info = null
                    error = null
                    onAuthenticated()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.auth_logout))
            }
            if (loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            info?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (modeRegister) {
                stringResource(R.string.auth_register_title)
            } else {
                stringResource(R.string.auth_login_title)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    info = null
                    val result = if (modeRegister) {
                        auth.register(email, password).map { }
                    } else {
                        auth.login(email, password).map { }
                    }
                    loading = false
                    result.onSuccess {
                        syncManager.enqueuePendingSessions()
                        onAuthenticated()
                    }.onFailure { e ->
                        error = e.message ?: "Auth failed"
                    }
                }
            },
            enabled = !loading && email.isNotBlank() && password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (modeRegister) {
                    stringResource(R.string.auth_register)
                } else {
                    stringResource(R.string.auth_login)
                },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                modeRegister = !modeRegister
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (modeRegister) {
                    stringResource(R.string.auth_switch_login)
                } else {
                    stringResource(R.string.auth_switch_register)
                },
            )
        }
        if (loading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
