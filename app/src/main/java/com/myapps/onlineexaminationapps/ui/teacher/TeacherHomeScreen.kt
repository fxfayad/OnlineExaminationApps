package com.myapps.onlineexaminationapps.ui.teacher

import androidx.compose.runtime.Composable

@Composable
fun TeacherHomeScreen(
    onCreateChapterClick: () -> Unit,
    onCreateQuestionClick: () -> Unit = {},
    onViewStudentAnswersClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onChapterClick: (String) -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    TeacherDashboardScreen(
        onCreateChapterClick = onCreateChapterClick,
        onCreateQuestionClick = onCreateQuestionClick,
        onViewStudentAnswersClick = onViewStudentAnswersClick,
        onAnalyticsClick = onAnalyticsClick,
        onChapterClick = onChapterClick,
        onLogoutClick = onLogoutClick
    )
}
