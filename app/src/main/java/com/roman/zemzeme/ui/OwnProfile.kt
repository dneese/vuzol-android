package com.roman.zemzeme.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Profile of the local user: avatar, display name and bio.
 *
 * The avatar stays on the device, the name and bio can be published to Nostr
 * as a NIP-01 metadata event (kind 0).
 */
object OwnProfile {
    private const val PREFS_NAME = "own_profile"
    private const val KEY_AVATAR = "avatar_path"
    private const val KEY_NAME = "display_name"
    private const val KEY_ABOUT = "about"

    data class State(
        val avatarPath: String? = null,
        val displayName: String = "",
        val about: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = State(
            avatarPath = prefs.getString(KEY_AVATAR, null),
            displayName = prefs.getString(KEY_NAME, "") ?: "",
            about = prefs.getString(KEY_ABOUT, "") ?: ""
        )
    }

    fun setAvatar(context: Context, path: String?) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AVATAR, path).apply()
        _state.value = _state.value.copy(avatarPath = path)
    }

    fun setDisplayName(context: Context, name: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NAME, name.trim()).apply()
        _state.value = _state.value.copy(displayName = name.trim())
    }

    fun setAbout(context: Context, about: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ABOUT, about.trim()).apply()
        _state.value = _state.value.copy(about = about.trim())
    }
}

/**
 * Small avatar of the local user: the uploaded picture when available,
 * otherwise a coloured letter.
 */
@Composable
fun OwnProfileAvatar(
    nickname: String,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val profile by OwnProfile.state.collectAsState()
    val bitmap = remember(profile.avatarPath) {
        profile.avatarPath?.let { path ->
            runCatching {
                android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = (size.value * 0.42f).sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
