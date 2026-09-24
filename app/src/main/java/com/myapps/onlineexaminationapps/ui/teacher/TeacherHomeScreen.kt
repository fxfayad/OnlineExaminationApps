package com.myapps.onlineexaminationapps.ui.teacher

import androidx.compose.runtime.Composable

@Composable
fun TeacherHomeScreen(
    onCreateChapterClick: () -> Unit,
    onCreateQuestionClick: () -> Unit = {},
    onViewStudentAnswersClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onChapterClick: (String) -> Unit = {},
    onEditQuestionClick: (String, String) -> Unit = { _, _ -> },
    onLogoutClick: () -> Unit = {}
) {
    TeacherDashboardScreen(
        onCreateChapterClick = onCreateChapterClick,
        onCreateQuestionClick = onCreateQuestionClick,
        onViewStudentAnswersClick = onViewStudentAnswersClick,
        onAnalyticsClick = onAnalyticsClick,
        onChapterClick = onChapterClick,
        onEditQuestionClick = onEditQuestionClick,
        onLogoutClick = onLogoutClick
    )
}
