package com.myapps.onlineexaminationapps.firebase

import android.util.Log
import com.myapps.onlineexaminationapps.model.ChapterAnalytics
import com.myapps.onlineexaminationapps.model.ExamResult
import com.myapps.onlineexaminationapps.model.TeacherAnalytics

class AnalyticsRepository {
    private val chapterRepository = ChapterRepository()
    private val questionRepository = QuestionRepository()
    private val submissionRepository = SubmissionRepository()

    suspend fun getTeacherAnalytics(teacherUid: String): Result<TeacherAnalytics> {
        Log.d("TEACHER_ANALYTICS_DEBUG", "getTeacherAnalytics called for teacherUid: $teacherUid")
        return try {
            val chaptersRes = chapterRepository.getTeacherChapters(teacherUid)
            if (chaptersRes.isFailure) {
                val e = chaptersRes.exceptionOrNull() ?: Exception("Failed to fetch chapters")
                Log.e("TEACHER_ANALYTICS_DEBUG", "Error fetching teacher chapters", e)
                return Result.failure(e)
            }

            val chapters = chaptersRes.getOrNull() ?: emptyList()
            Log.d("TEACHER_ANALYTICS_DEBUG", "Teacher chapters count: ${chapters.size}")

            if (chapters.isEmpty()) {
                return Result.success(
                    TeacherAnalytics(
                        totalChapters = 0,
                        totalQuestions = 0,
                        totalStudents = 0,
                        totalSubmissions = 0,
                        completedSubmissions = 0,
                        pendingSubmissions = 0,
                        averagePercentage = 0.0,
                        chapterAnalyticsList = emptyList(),
                        recentSubmissions = emptyList()
                    )
                )
            }

            val questionsRes = questionRepository.getTeacherQuestions(teacherUid)
            val teacherQuestions = questionsRes.getOrNull() ?: emptyList()
            Log.d("TEACHER_ANALYTICS_DEBUG", "Teacher questions count: ${teacherQuestions.size}")

            val allSubmissions = mutableListOf<ExamResult>()
            val chapterAnalyticsList = mutableListOf<ChapterAnalytics>()

            chapters.forEach { chap ->
                val subRes = submissionRepository.getSubmissionsForChapter(chap.id)
                val subList = subRes.getOrNull() ?: emptyList()
                allSubmissions.addAll(subList)

                val qCount = teacherQuestions.count { it.chapterId == chap.id }
                val subCount = subList.size
                val completedCount = subList.count { it.status == "Completed" }
                val pendingCount = subList.count { it.status == "Pending Teacher Evaluation" }
                val uniqueStudents = subList.map { it.studentId }.filter { it.isNotBlank() }.distinct().size

                val validSubs = subList.filter { it.totalMarks > 0 }
                val sumObtained = validSubs.sumOf { it.obtainedMarks }
                val sumTotal = validSubs.sumOf { it.totalMarks }
                val avgPct = if (sumTotal > 0) (sumObtained.toDouble() / sumTotal.toDouble()) * 100.0 else 0.0

                chapterAnalyticsList.add(
                    ChapterAnalytics(
                        chapterId = chap.id,
                        chapterName = chap.name,
                        questionCount = qCount,
                        uniqueStudentsCount = uniqueStudents,
                        totalSubmissions = subCount,
                        completedSubmissions = completedCount,
                        pendingSubmissions = pendingCount,
                        averagePercentage = avgPct,
                        totalMarks = sumTotal
                    )
                )
            }

            val totalSubmissionsCount = allSubmissions.size
            val completedSubmissionsCount = allSubmissions.count { it.status == "Completed" }
            val pendingSubmissionsCount = allSubmissions.count { it.status == "Pending Teacher Evaluation" }
            val totalUniqueStudents = allSubmissions.map { it.studentId }.filter { it.isNotBlank() }.distinct().size

            val overallValidSubs = allSubmissions.filter { it.totalMarks > 0 }
            val overallSumObtained = overallValidSubs.sumOf { it.obtainedMarks }
            val overallSumTotal = overallValidSubs.sumOf { it.totalMarks }
            val overallAvgPct = if (overallSumTotal > 0) (overallSumObtained.toDouble() / overallSumTotal.toDouble()) * 100.0 else 0.0

            val recentSubs = allSubmissions
                .sortedByDescending { it.submittedAt?.seconds ?: 0L }
                .take(5)

            Log.d(
                "TEACHER_ANALYTICS_DEBUG",
                "Analytics computed: Chapters=${chapters.size}, Questions=${teacherQuestions.size}, Students=$totalUniqueStudents, Submissions=$totalSubmissionsCount, Completed=$completedSubmissionsCount, Pending=$pendingSubmissionsCount, AvgPct=$overallAvgPct%"
            )

            Result.success(
                TeacherAnalytics(
                    totalChapters = chapters.size,
                    totalQuestions = teacherQuestions.size,
                    totalStudents = totalUniqueStudents,
                    totalSubmissions = totalSubmissionsCount,
                    completedSubmissions = completedSubmissionsCount,
                    pendingSubmissions = pendingSubmissionsCount,
                    averagePercentage = overallAvgPct,
                    chapterAnalyticsList = chapterAnalyticsList,
                    recentSubmissions = recentSubs
                )
            )
        } catch (e: Exception) {
            Log.e("TEACHER_ANALYTICS_DEBUG", "getTeacherAnalytics exception", e)
            Result.failure(e)
        }
    }
}
