package com.myapps.onlineexaminationapps.model

data class TeacherAnalytics(
    val totalChapters: Int = 0,
    val totalQuestions: Int = 0,
    val totalStudents: Int = 0,
    val totalSubmissions: Int = 0,
    val completedSubmissions: Int = 0,
    val pendingSubmissions: Int = 0,
    val averagePercentage: Double = 0.0,
    val chapterAnalyticsList: List<ChapterAnalytics> = emptyList(),
    val recentSubmissions: List<ExamResult> = emptyList()
)

data class ChapterAnalytics(
    val chapterId: String = "",
    val chapterName: String = "",
    val questionCount: Int = 0,
    val uniqueStudentsCount: Int = 0,
    val totalSubmissions: Int = 0,
    val completedSubmissions: Int = 0,
    val pendingSubmissions: Int = 0,
    val averagePercentage: Double = 0.0,
    val totalMarks: Int = 0
)
