package com.example.chess.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun run(block: suspend () -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            runCatching { block() }
                .onSuccess { onDone() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun emailSignIn(email: String, password: String, onDone: () -> Unit) =
        run({ auth.signIn(email, password) }, onDone)

    fun register(email: String, password: String, name: String, onDone: () -> Unit) =
        run({ auth.register(email, password, name) }, onDone)

    fun google(activity: Activity, onDone: () -> Unit) =
        run({ auth.signInWithGoogle(activity) }, onDone)

    fun offline(name: String, onDone: () -> Unit) =
        run({ auth.continueOffline(name) }, onDone)
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ChessOS", style = MaterialTheme.typography.displaySmall)
        Text("Analytics, training, and engine work in one board.")
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.emailSignIn(email, password, onSignedIn) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Sign in")
        }
        OutlinedButton(onClick = { vm.register(email, password, name, onSignedIn) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Create account")
        }
        OutlinedButton(
            onClick = { vm.google(context as Activity, onSignedIn) },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue with Google") }
        OutlinedButton(onClick = { vm.offline(name, onSignedIn) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Continue offline (local + later Firestore sync)")
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Drop google-services.json into app/ to enable Firebase Auth and Firestore.", style = MaterialTheme.typography.bodySmall)
    }
}
