package com.example.test_pj.data.user

data class UserInfo(
    val id: String = "",
    val displayName: String = "ゲスト",
    val email: String = "",
    val photoUrl: String = "",
    val isGoogleUser: Boolean = false,
) {
    val mem0UserId: String get() = if (id.isNotBlank()) id else "memly-guest"
    val initial: String get() = displayName.firstOrNull()?.uppercase() ?: "G"
}
