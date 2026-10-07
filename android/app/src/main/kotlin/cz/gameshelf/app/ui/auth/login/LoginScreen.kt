package cz.gameshelf.app.ui.auth.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.gameshelf.app.R
import cz.gameshelf.app.ui.auth.AuthFormColumn
import cz.gameshelf.app.ui.auth.FormErrorText
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.components.PasswordField
import cz.gameshelf.app.ui.components.ProgressButton
import cz.gameshelf.app.ui.theme.GameShelfTheme
import kotlinx.coroutines.Dispatchers

@Composable
fun LoginRoute(
    onRegisterClick: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    // Text field state is collected without a dispatch so typing never races the IME.
    val state by viewModel.uiState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onRegisterClick = onRegisterClick,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    Scaffold { padding ->
        AuthFormColumn(Modifier.padding(padding).consumeWindowInsets(padding)) {
            Spacer(Modifier.height(32.dp))
            Icon(
                painter = painterResource(R.drawable.ic_sports_esports),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.login_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
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
                    .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
            )
            PasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.field_password),
                error = state.passwordError?.asString(),
                imeAction = ImeAction.Done,
                onDone = onSubmit,
            )
            state.errorMessage?.let { FormErrorText(it.asString()) }
            Spacer(Modifier.height(8.dp))
            ProgressButton(
                text = stringResource(R.string.action_login),
                onClick = onSubmit,
                isLoading = state.isSubmitting,
            )
            TextButton(onClick = onRegisterClick, enabled = !state.isSubmitting) {
                Text(stringResource(R.string.login_no_account))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    GameShelfTheme(dynamicColor = false) {
        LoginScreen(
            state = LoginUiState(email = "collector@example.com"),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onRegisterClick = {},
        )
    }
}
