package com.myapps.onlineexaminationapps.ui.home

import androidx.compose.runtime.Composable

@Composable
fun StudentHomeScreen(
    onChapterClick: (String) -> Unit,
    onResultClick: (String) -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    StudentDashboardScreen(
        onChapterClick = onChapterClick,
        onResultClick = onResultClick,
        onLogoutClick = onLogoutClick
    )
}
