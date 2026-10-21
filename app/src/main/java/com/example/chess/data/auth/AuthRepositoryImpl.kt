package com.example.chess.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.chess.data.local.LocalJsonStore
import com.example.chess.data.local.toDomain
import com.example.chess.data.local.toDto
import com.example.chess.domain.chess.newId
import com.example.chess.domain.model.AuthState
import com.example.chess.domain.model.ThemePreference
import com.example.chess.domain.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth?,
    private val firestore: FirebaseFirestore?,
    private val local: LocalJsonStore,
    private val prefs: DataStore<Preferences>,
) : AuthRepository {
    private val guestUidKey = stringPreferencesKey("guest_uid")
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _state

    init {
        scope.launch { refresh() }
        firebaseAuth?.addAuthStateListener { scope.launch { refresh() } }
    }

    override suspend fun signInEmail(email: String, password: String) {
        val auth = firebaseAuth ?: error("Add google-services.json to enable Firebase email login, or use Guest.")
        auth.signInWithEmailAndPassword(email, password).await()
        refresh()
    }

    override suspend fun registerEmail(email: String, password: String, name: String) {
        val auth = firebaseAuth ?: error("Add google-services.json to enable Firebase registration, or use Guest.")
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build(),
        )?.await()
        persistProfile(
            UserProfile(
                uid = result.user?.uid ?: error("Missing uid"),
                email = email,
                displayName = name,
            ),
        )
        refresh()
    }

    override suspend fun signInGoogle(idToken: String) {
        val auth = firebaseAuth ?: error("Firebase is not configured")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        val user = auth.currentUser ?: return
        persistProfile(
            UserProfile(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName ?: "Player",
            ),
        )
        refresh()
    }

    override suspend fun signInGuest(name: String) {
        val uid = prefs.data.map { it[guestUidKey] }.first() ?: newId().also { id ->
            prefs.edit { it[guestUidKey] = id }
        }
        persistProfile(
            local.current().profile?.toDomain()?.takeIf { it.uid == uid }
                ?: UserProfile(uid = uid, email = null, displayName = name),
        )
        refresh()
    }

    override suspend fun signOut() {
        firebaseAuth?.signOut()
        prefs.edit { it.remove(guestUidKey) }
        _state.value = AuthState.SignedOut
    }

    override suspend fun currentProfile(): UserProfile? =
        (authState.value as? AuthState.SignedIn)?.profile

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val firebaseUser = firebaseAuth?.currentUser
        val guest = prefs.data.map { it[guestUidKey] }.first()
        when {
            firebaseUser != null -> {
                val stored = loadRemote(firebaseUser.uid)
                val profile = stored ?: UserProfile(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName ?: "Player",
                )
                persistProfile(profile)
                _state.value = AuthState.SignedIn(profile)
            }
            guest != null -> {
                val profile = local.current().profile?.toDomain()
                    ?: UserProfile(uid = guest, email = null, displayName = "Trainer")
                _state.value = AuthState.SignedIn(profile)
            }
            else -> _state.value = AuthState.SignedOut
        }
    }

    private suspend fun persistProfile(profile: UserProfile) {
        local.update { it.copy(profile = profile.toDto()) }
        firestore?.collection("users")?.document(profile.uid)?.set(
            mapOf(
                "email" to profile.email,
                "displayName" to profile.displayName,
                "chessComUsername" to profile.chessComUsername,
                "lichessUsername" to profile.lichessUsername,
                "theme" to profile.theme.name,
                "engineDepth" to profile.engineDepth,
                "engineMoveTimeMs" to profile.engineMoveTimeMs,
                "skillLevel" to profile.skillLevel,
            ),
        )?.await()
    }

    private suspend fun loadRemote(uid: String): UserProfile? {
        val snap = firestore?.collection("users")?.document(uid)?.get()?.await() ?: return null
        if (!snap.exists()) return null
        return UserProfile(
            uid = uid,
            email = snap.getString("email"),
            displayName = snap.getString("displayName") ?: "Player",
            chessComUsername = snap.getString("chessComUsername").orEmpty(),
            lichessUsername = snap.getString("lichessUsername").orEmpty(),
            lichessToken = snap.getString("lichessToken").orEmpty(),
            theme = runCatching { ThemePreference.valueOf(snap.getString("theme") ?: "SYSTEM") }
                .getOrDefault(ThemePreference.SYSTEM),
            engineDepth = snap.getLong("engineDepth")?.toInt() ?: 12,
            engineMoveTimeMs = snap.getLong("engineMoveTimeMs") ?: 800L,
            skillLevel = snap.getLong("skillLevel")?.toInt() ?: 8,
        )
    }
}
