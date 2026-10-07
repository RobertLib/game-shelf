package cz.gameshelf.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.components.PasswordField

@Composable
fun ChangePasswordDialog(
    form: ChangePasswordForm,
    onChange: ((ChangePasswordForm) -> ChangePasswordForm) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.change_password_info), style = MaterialTheme.typography.bodyMedium)
                PasswordField(
                    value = form.currentPassword,
                    onValueChange = { v -> onChange { it.copy(currentPassword = v) } },
                    label = stringResource(R.string.field_current_password),
                    error = form.currentPasswordError?.asString(),
                    enabled = !form.isSubmitting,
                )
                PasswordField(
                    value = form.newPassword,
                    onValueChange = { v -> onChange { it.copy(newPassword = v) } },
                    label = stringResource(R.string.field_new_password),
                    error = form.newPasswordError?.asString(),
                    supportingText = stringResource(R.string.password_requirements),
                    autofillType = ContentType.NewPassword,
                    enabled = !form.isSubmitting,
                )
                PasswordField(
                    value = form.confirmation,
                    onValueChange = { v -> onChange { it.copy(confirmation = v) } },
                    label = stringResource(R.string.field_new_password_confirm),
                    error = form.confirmationError?.asString(),
                    autofillType = ContentType.NewPassword,
                    imeAction = ImeAction.Done,
                    onDone = onSubmit,
                    enabled = !form.isSubmitting,
                )
                form.errorMessage?.let {
                    Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !form.isSubmitting) {
                Text(stringResource(R.string.action_change_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !form.isSubmitting) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun DeleteAccountDialog(
    form: DeleteAccountForm,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.delete_account_message), style = MaterialTheme.typography.bodyMedium)
                PasswordField(
                    value = form.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.field_password),
                    error = form.passwordError?.asString(),
                    imeAction = ImeAction.Done,
                    onDone = onSubmit,
                    enabled = !form.isSubmitting,
                )
                form.errorMessage?.let {
                    Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !form.isSubmitting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_delete_account)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !form.isSubmitting) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
