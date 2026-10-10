package cz.gameshelf.app.ui.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.gameshelf.app.R
import cz.gameshelf.app.data.auth.AuthRepository
import cz.gameshelf.app.data.auth.SignOutReason
import cz.gameshelf.app.domain.model.onFailure
import cz.gameshelf.app.ui.auth.AuthValidation
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.normalizedText
import cz.gameshelf.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val errorMessage: UiText? = null,
    val isSubmitting: Boolean = false,
    /** Why the app ended the previous session by itself, shown above the form. */
    val notice: UiText? = null,
)

/**
 * On success the session changes and the app root switches to the main graph. When the app ended the previous
 * session by itself, the screen says why (mobile-spec.md, "API basics") until the user signs in or leaves it.
 */
class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.signOutReason.collect { reason -> _uiState.update { it.copy(notice = reason?.toUiText()) } }
        }
    }

    /** The user leaves the screen (e.g. to create an account): the notice is not shown again. */
    fun onLeave() = authRepository.dismissSignOutReason()

    override fun onCleared() = onLeave()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val emailError = AuthValidation.signInEmail(state.email)
        val passwordError = AuthValidation.requiredPassword(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            authRepository.login(state.email.normalizedText(), state.password).onFailure { error ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = error.toUiText()) }
            }
        }
    }

    private fun SignOutReason.toUiText() = when (this) {
        SignOutReason.SESSION_EXPIRED -> UiText(R.string.login_session_expired)
        SignOutReason.ACCOUNT_DELETED -> UiText(R.string.error_account_deleted)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(appContainer.authRepository) }
        }
    }
}
