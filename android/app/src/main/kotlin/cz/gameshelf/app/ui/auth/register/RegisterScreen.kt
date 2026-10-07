package cz.gameshelf.app.ui.auth.register

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.gameshelf.app.R
import cz.gameshelf.app.ui.auth.AuthFormColumn
import cz.gameshelf.app.ui.auth.FormErrorText
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.components.NavigationIconButton
import cz.gameshelf.app.ui.components.PasswordField
import cz.gameshelf.app.ui.components.ProgressButton
import cz.gameshelf.app.ui.theme.GameShelfTheme
import kotlinx.coroutines.Dispatchers

@Composable
fun RegisterRoute(
    onBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    RegisterScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordConfirmationChange = viewModel::onPasswordConfirmationChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmationChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.register_title)) },
                navigationIcon = { NavigationIconButton(onClick = onBack) },
            )
        },
    ) { padding ->
        AuthFormColumn(Modifier.padding(padding).consumeWindowInsets(padding)) {
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.field_email)) },
                singleLine = true,
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(it.asString()) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.EmailAddress + ContentType.NewUsername },
            )
            OutlinedTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text(stringResource(R.string.field_display_name)) },
                singleLine = true,
                isError = state.displayNameError != null,
                supportingText = state.displayNameError?.let { { Text(it.asString()) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.PersonFullName },
            )
            PasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.field_password),
                error = state.passwordError?.asString(),
                supportingText = stringResource(R.string.password_requirements),
                autofillType = ContentType.NewPassword,
            )
            PasswordField(
                value = state.passwordConfirmation,
                onValueChange = onPasswordConfirmationChange,
                label = stringResource(R.string.field_password_confirm),
                error = state.passwordConfirmationError?.asString(),
                autofillType = ContentType.NewPassword,
                imeAction = ImeAction.Done,
                onDone = onSubmit,
            )
            state.errorMessage?.let { FormErrorText(it.asString()) }
            Spacer(Modifier.height(8.dp))
            ProgressButton(
                text = stringResource(R.string.action_register),
                onClick = onSubmit,
                isLoading = state.isSubmitting,
            )
            TextButton(onClick = onBack, enabled = !state.isSubmitting) {
                Text(stringResource(R.string.register_have_account))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    GameShelfTheme(dynamicColor = false) {
        RegisterScreen(
            state = RegisterUiState(email = "collector@example.com"),
            onEmailChange = {},
            onDisplayNameChange = {},
            onPasswordChange = {},
            onPasswordConfirmationChange = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
