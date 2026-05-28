package com.example.test_pj.data.user

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserRepository(context: Context) {

    private val dataStore = context.userDataStore

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
        val PHOTO_URL = stringPreferencesKey("photo_url")
        val IS_GOOGLE_USER = booleanPreferencesKey("is_google_user")
    }

    val userInfo: Flow<UserInfo> = dataStore.data.map { prefs ->
        val id = prefs[Keys.USER_ID] ?: ""
        UserInfo(
            id = id,
            displayName = prefs[Keys.DISPLAY_NAME] ?: "ゲスト",
            email = prefs[Keys.EMAIL] ?: "",
            photoUrl = prefs[Keys.PHOTO_URL] ?: "",
            isGoogleUser = prefs[Keys.IS_GOOGLE_USER] ?: false,
        )
    }

    suspend fun saveGoogleUser(credential: GoogleIdTokenCredential) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = credential.id
            prefs[Keys.DISPLAY_NAME] = credential.displayName ?: credential.id
            prefs[Keys.EMAIL] = credential.id
            prefs[Keys.PHOTO_URL] = credential.profilePictureUri?.toString() ?: ""
            prefs[Keys.IS_GOOGLE_USER] = true
        }
    }

    suspend fun updateDisplayName(name: String) {
        dataStore.edit { prefs ->
            if (prefs[Keys.USER_ID].isNullOrBlank()) {
                prefs[Keys.USER_ID] = UUID.randomUUID().toString()
            }
            prefs[Keys.DISPLAY_NAME] = name
        }
    }

    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = UUID.randomUUID().toString()
            prefs[Keys.DISPLAY_NAME] = "ゲスト"
            prefs[Keys.EMAIL] = ""
            prefs[Keys.PHOTO_URL] = ""
            prefs[Keys.IS_GOOGLE_USER] = false
        }
    }
}
