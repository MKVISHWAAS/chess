package com.example.chess.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.chess.BuildConfig
import com.example.chess.data.repo.ChessRepository
import com.example.chess.domain.model.AuthState
import com.example.chess.domain.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chessRepository: ChessRepository,
) {
    private val firebaseReady = runCatching { FirebaseApp.getInstance(); true }.getOrDefault(false)
    private val auth: FirebaseAuth? = if (firebaseReady) FirebaseAuth.getInstance() else null

    val state: Flow<AuthState> = combine(
        callbackFlow {
            if (auth == null) {
                trySend(null)
                awaitClose { }
                return@callbackFlow
            }
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        },
        chessRepository.snapshot,
    ) { user, snap ->
        when {
            !firebaseReady -> AuthState.SignedOut
            user == null -> AuthState.SignedOut
            else -> AuthState.SignedIn(
                snap.profile?.copy(uid = user.uid, email = user.email.orEmpty(), displayName = user.displayName ?: snap.profile.displayName)
                    ?: UserProfile(uid = user.uid, email = user.email.orEmpty(), displayName = user.displayName.orEmpty()),
            )
        }
    }

    suspend fun signIn(email: String, password: String) {
        val firebase = requireAuth()
        firebase.signInWithEmailAndPassword(email, password).await()
        syncProfile()
    }

    suspend fun register(email: String, password: String, name: String) {
        val firebase = requireAuth()
        firebase.createUserWithEmailAndPassword(email, password).await()
        val user = firebase.currentUser ?: return
        chessRepository.saveProfile(UserProfile(uid = user.uid, email = email, displayName = name))
        chessRepository.pullCloud()
    }

    suspend fun signInWithGoogle(activityContext: Context) {
        val firebase = requireAuth()
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        require(webClientId.isNotBlank()) { "Set GOOGLE_WEB_CLIENT_ID in local.properties" }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        val idToken = (credential as? CustomCredential)
            ?.takeIf { it.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL }
            ?.let { GoogleIdTokenCredential.createFrom(it.data).idToken }
            ?: error("Google ID token missing")
        firebase.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        syncProfile()
    }

    fun signOut() {
        auth?.signOut()
    }

    suspend fun continueOffline(name: String) {
        val existing = chessRepository.current().profile
        chessRepository.saveProfile(
            existing?.copy(displayName = name.ifBlank { existing.displayName.ifBlank { "Coach" } })
                ?: UserProfile(uid = "local", displayName = name.ifBlank { "Coach" }),
        )
    }

    private suspend fun syncProfile() {
        val user = auth?.currentUser ?: return
        val existing = chessRepository.current().profile
        chessRepository.saveProfile(
            (existing ?: UserProfile(uid = user.uid)).copy(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = existing?.displayName?.ifBlank { user.displayName.orEmpty() } ?: user.displayName.orEmpty(),
            ),
        )
        chessRepository.pullCloud()
    }

    private fun requireAuth(): FirebaseAuth =
        auth ?: error("Add app/google-services.json from your Firebase project to enable sign-in.")
}
