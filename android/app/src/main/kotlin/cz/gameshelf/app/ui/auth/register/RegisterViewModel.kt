package cz.gameshelf.app.ui.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.gameshelf.app.data.auth.AuthRepository
import cz.gameshelf.app.domain.model.onFailure
import cz.gameshelf.app.ui.auth.AuthValidation
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val displayName: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val emailError: UiText? = null,
    val displayNameError: UiText? = null,
    val passwordError: UiText? = null,
    val passwordConfirmationError: UiText? = null,
    val errorMessage: UiText? = null,
    val isSubmitting: Boolean = false,
)

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }

    fun onDisplayNameChange(value: String) =
        _uiState.update { it.copy(displayName = value, displayNameError = null, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun onPasswordConfirmationChange(value: String) =
        _uiState.update { it.copy(passwordConfirmation = value, passwordConfirmationError = null, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val validated = state.copy(
            emailError = AuthValidation.email(state.email),
            displayNameError = AuthValidation.displayName(state.displayName),
            passwordError = AuthValidation.newPassword(state.password),
            passwordConfirmationError = AuthValidation.passwordConfirmation(state.password, state.passwordConfirmation),
        )
        if (listOfNotNull(
                validated.emailError,
                validated.displayNameError,
                validated.passwordError,
                validated.passwordConfirmationError,
            ).isNotEmpty()
        ) {
            _uiState.value = validated
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            authRepository.register(
                email = state.email.trim(),
                password = state.password,
                displayName = state.displayName.trim().ifEmpty { null },
            ).onFailure { error ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = error.toUiText()) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { RegisterViewModel(appContainer.authRepository) }
        }
    }
}
