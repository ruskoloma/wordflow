package com.rsln.wordflow.ui.screens.login

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.ui.theme.*

/**
 * Two-step passwordless sign-in. Step 1 collects an email; step 2
 * collects the 6-digit code the backend had Clerk email to that
 * address. No passwords involved. On success, MainActivity's auth
 * gate picks up the state change and swaps this screen out for
 * the main nav.
 */
@Composable
fun LoginScreen(
    app: WordFlowApp,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory(app.container))
) {
    val step by viewModel.step.collectAsState()
    val email by viewModel.email.collectAsState()
    val code by viewModel.code.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(containerColor = Background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "WordFlow",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )

                Crossfade(targetState = step, label = "login-step") { current ->
                    when (current) {
                        LoginViewModel.Step.Email -> EmailStep(
                            email = email,
                            isSubmitting = isSubmitting,
                            error = error,
                            onEmailChange = viewModel::updateEmail,
                            onSubmit = viewModel::sendCode
                        )
                        LoginViewModel.Step.Code -> CodeStep(
                            email = email,
                            code = code,
                            isSubmitting = isSubmitting,
                            error = error,
                            onCodeChange = viewModel::updateCode,
                            onSubmit = viewModel::verifyCode,
                            onBack = viewModel::backToEmail,
                            onResend = viewModel::sendCode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailStep(
    email: String,
    isSubmitting: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Sign in with email",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurfaceVariant
        )
        Text(
            text = "We'll send you a 6-digit code. No password needed.",
            style = MaterialTheme.typography.bodySmall,
            color = Outline
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = OutlineVariant
            )
        )

        ErrorRow(error)

        Button(
            onClick = onSubmit,
            enabled = !isSubmitting && email.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = OnPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Send code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CodeStep(
    email: String,
    code: String,
    isSubmitting: Boolean,
    error: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onResend: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Enter the code",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurfaceVariant
        )
        Text(
            text = "Sent to $email",
            style = MaterialTheme.typography.bodySmall,
            color = Outline
        )

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("6-digit code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = OutlineVariant
            )
        )

        ErrorRow(error)

        Button(
            onClick = onSubmit,
            enabled = !isSubmitting && code.length in 4..6,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = OnPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Verify",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack, enabled = !isSubmitting) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Change email", color = Primary)
            }
            TextButton(onClick = onResend, enabled = !isSubmitting) {
                Text("Resend code", color = Primary)
            }
        }
    }
}

@Composable
private fun ErrorRow(error: String?) {
    if (error != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
