package com.zwiedzak.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zwiedzak.ui.auth.AuthState
import com.zwiedzak.ui.auth.AuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()

    var isLoginTab by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authViewModel.checkAuthStatus(context)
    }
    LaunchedEffect(authState is AuthState.VerificationRequired) {
        if (authState is AuthState.VerificationRequired) password = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (authState is AuthState.VerificationRequired) "Potwierdzenie rejestracji" else if (isLoginTab) "Logowanie" else "Rejestracja konta") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Powrót"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val verificationState = authState as? AuthState.VerificationRequired
                    if (verificationState != null) {
                        RegistrationVerification(
                            state = verificationState,
                            onVerify = { authViewModel.verifyRegistration(context, it) },
                            onResend = authViewModel::resendRegistration,
                            onCancel = authViewModel::cancelRegistration
                        )
                    } else {
                        Text(
                            text = if (isLoginTab) "Zaloguj się do Zwiedzak" else "Załóż nowe konto",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )

                        // Tab selector
                        TabRow(selectedTabIndex = if (isLoginTab) 0 else 1) {
                            Tab(
                                selected = isLoginTab,
                                enabled = authState !is AuthState.Loading,
                                onClick = {
                                    isLoginTab = true
                                    authViewModel.clearError()
                                },
                                text = { Text("Logowanie") }
                            )
                            Tab(
                                selected = !isLoginTab,
                                enabled = authState !is AuthState.Loading,
                                onClick = {
                                    isLoginTab = false
                                    authViewModel.clearError()
                                },
                                text = { Text("Rejestracja") }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Email input
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Adres e-mail") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Password input
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Hasło") },
                            supportingText = {
                                if (!isLoginTab) {
                                    Text("Wymagane minimum 10 znaków")
                                }
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Error message display
                        val currentState = authState
                        if (currentState is AuthState.Unauthenticated && !currentState.errorMessage.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentState.errorMessage,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Success message if logged in
                        if (currentState is AuthState.Authenticated) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Zalogowano pomyślnie!",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = currentState.email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = onBackClick) {
                                        Text("Powrót do aplikacji")
                                    }
                                }
                            }
                        } else {
                            // Submit button
                            Button(
                                onClick = {
                                    if (isLoginTab) {
                                        authViewModel.login(context, email, password)
                                    } else {
                                        authViewModel.register(email, password)
                                    }
                                },
                                enabled = currentState !is AuthState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                if (currentState is AuthState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(if (isLoginTab) "Zaloguj się" else "Zarejestruj się")
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun RegistrationVerification(
    state: AuthState.VerificationRequired,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    var code by remember(state.email) { mutableStateOf("") }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.resendAvailableAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (now >= state.resendAvailableAt) break
            delay(1000)
        }
    }
    val remainingSeconds = ((state.resendAvailableAt - now).coerceAtLeast(0) + 999) / 1000

    Text("Potwierdź adres e-mail", style = MaterialTheme.typography.headlineSmall)
    Text("Wpisz kod wysłany na adres ${state.email}. Początkowy kod jest ważny przez ${state.expiresIn} s.")
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("Kod weryfikacyjny") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !state.loading,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    state.errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
    }
    state.message?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
    }
    Button(
        onClick = { onVerify(code) },
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Text("Potwierdź kod")
        }
    }
    TextButton(onClick = onResend, enabled = !state.loading && remainingSeconds == 0L) {
        Text(if (remainingSeconds > 0) "Wyślij kod ponownie za ${remainingSeconds} s" else "Wyślij kod ponownie")
    }
    TextButton(onClick = onCancel, enabled = !state.loading) {
        Text("Wróć do logowania lub rejestracji")
    }
}
