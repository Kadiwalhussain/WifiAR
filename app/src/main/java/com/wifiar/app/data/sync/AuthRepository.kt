package com.wifiar.app.data.sync

import com.wifiar.app.data.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: WifiArApi = ApiClient.api,
    private val tokenStore: TokenStore,
) {
    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn
    val email: String? get() = tokenStore.email

    fun bearer(): String? {
        val token = tokenStore.accessToken ?: return null
        return "Bearer $token"
    }

    suspend fun register(email: String, password: String): Result<UserOut> =
        withContext(Dispatchers.IO) {
            runCatching {
                val user = api.register(RegisterRequest(email.trim().lowercase(), password))
                // Auto-login after register for smoother UX.
                val token = api.login(LoginRequest(email.trim().lowercase(), password))
                tokenStore.accessToken = token.accessToken
                tokenStore.email = user.email
                user
            }
        }

    suspend fun login(email: String, password: String): Result<TokenResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = api.login(LoginRequest(email.trim().lowercase(), password))
                tokenStore.accessToken = token.accessToken
                tokenStore.email = email.trim().lowercase()
                token
            }
        }

    fun logout() {
        tokenStore.clear()
    }
}
