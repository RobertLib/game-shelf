package cz.gameshelf.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.gameshelf.app.R
import cz.gameshelf.app.data.auth.AuthRepository
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.ui.auth.AuthValidation
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toDeleteAccountUiText
import cz.gameshelf.app.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    /** Non-null while the change-password dialog is open. */
    val changePassword: ChangePasswordForm? = null,
    /** Non-null while the delete-account dialog is open. */
    val deleteAccount: DeleteAccountForm? = null,
)

data class ChangePasswordForm(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmation: String = "",
    val currentPasswordError: UiText? = null,
    val newPasswordError: UiText? = null,
    val confirmationError: UiText? = null,
    val errorMessage: UiText? = null,
    val isSubmitting: Boolean = false,
)

data class DeleteAccountForm(
    val password: String = "",
    val passwordError: UiText? = null,
    val errorMessage: UiText? = null,
    val isSubmitting: Boolean = false,
)

sealed interface ProfileEvent {
    data class ShowMessage(val message: UiText) : ProfileEvent
}

/** Signing out or deleting the account ends the session; the app root then shows the login. */
class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                if (session is SessionState.SignedIn) _uiState.update { it.copy(user = session.user) }
            }
        }
        // Best effort refresh of the cached profile; the stored copy is shown meanwhile.
        viewModelScope.launch { authRepository.refreshCurrentUser() }
    }

    fun openChangePassword() = _uiState.update { it.copy(changePassword = ChangePasswordForm()) }

    fun dismissChangePassword() = _uiState.update {
        if (it.changePassword?.isSubmitting == true) it else it.copy(changePassword = null)
    }

    fun updateChangePassword(transform: (ChangePasswordForm) -> ChangePasswordForm) = _uiState.update { state ->
        state.copy(
            changePassword = state.changePassword?.let(transform)?.copy(
                currentPasswordError = null,
                newPasswordError = null,
                confirmationError = null,
                errorMessage = null,
            ),
        )
    }

    fun submitChangePassword() {
        val form = _uiState.value.changePassword ?: return
        if (form.isSubmitting) return
        val validated = form.copy(
            currentPasswordError = AuthValidation.requiredPassword(form.currentPassword),
            newPasswordError = AuthValidation.newPassword(form.newPassword),
            confirmationError = AuthValidation.passwordConfirmation(form.newPassword, form.confirmation),
        )
        if (listOfNotNull(validated.currentPasswordError, validated.newPasswordError, validated.confirmationError).isNotEmpty()) {
            _uiState.update { it.copy(changePassword = validated) }
            return
        }
        _uiState.update { it.copy(changePassword = form.copy(isSubmitting = true, errorMessage = null)) }
        viewModelScope.launch {
            when (val result = authRepository.changePassword(form.currentPassword, form.newPassword)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(changePassword = null) }
                    _events.send(ProfileEvent.ShowMessage(UiText(R.string.password_changed)))
                }
                is ApiResult.Failure -> _uiState.update { state ->
                    state.copy(
                        changePassword = state.changePassword?.copy(
                            isSubmitting = false,
                            errorMessage = result.error.toUiText(),
                        ),
                    )
                }
            }
        }
    }

    fun openDeleteAccount() = _uiState.update { it.copy(deleteAccount = DeleteAccountForm()) }

    fun dismissDeleteAccount() = _uiState.update {
        if (it.deleteAccount?.isSubmitting == true) it else it.copy(deleteAccount = null)
    }

    fun updateDeletePassword(value: String) = _uiState.update { state ->
        state.copy(deleteAccount = state.deleteAccount?.copy(password = value, passwordError = null, errorMessage = null))
    }

    fun submitDeleteAccount() {
        val form = _uiState.value.deleteAccount ?: return
        if (form.isSubmitting) return
        val passwordError = AuthValidation.requiredPassword(form.password)
        if (passwordError != null) {
            _uiState.update { it.copy(deleteAccount = form.copy(passwordError = passwordError)) }
            return
        }
        _uiState.update { it.copy(deleteAccount = form.copy(isSubmitting = true, errorMessage = null)) }
        viewModelScope.launch {
            val result = authRepository.deleteAccount(form.password)
            if (result is ApiResult.Failure) {
                _uiState.update { state ->
                    state.copy(
                        deleteAccount = state.deleteAccount?.copy(
                            isSubmitting = false,
                            errorMessage = result.error.toDeleteAccountUiText(),
                        ),
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(appContainer.authRepository) }
        }
    }
}
