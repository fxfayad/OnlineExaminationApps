package com.myapps.onlineexaminationapps.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.myapps.onlineexaminationapps.ui.SignUpChoiceScreen
import com.myapps.onlineexaminationapps.ui.home.ResultScreen
import com.myapps.onlineexaminationapps.ui.home.ReviewAnswersScreen
import com.myapps.onlineexaminationapps.ui.home.StudentChapterScreen
import com.myapps.onlineexaminationapps.ui.home.StudentExamViewModel
import com.myapps.onlineexaminationapps.ui.home.StudentHomeScreen
import com.myapps.onlineexaminationapps.ui.home.StudentQuestionsScreen
import com.myapps.onlineexaminationapps.ui.location.LocationPermissionScreen
import com.myapps.onlineexaminationapps.ui.login.LoginScreen
import com.myapps.onlineexaminationapps.ui.signup.SignUpScreen
import com.myapps.onlineexaminationapps.ui.splash.SplashScreen
import com.myapps.onlineexaminationapps.ui.teacher.ChapterListScreen
import com.myapps.onlineexaminationapps.ui.teacher.CreateChapterScreen
import com.myapps.onlineexaminationapps.ui.teacher.CreateQuestionScreen
import com.myapps.onlineexaminationapps.ui.teacher.QuestionManagementScreen
import com.myapps.onlineexaminationapps.ui.teacher.TeacherAnalyticsScreen
import com.myapps.onlineexaminationapps.ui.teacher.TeacherDashboardScreen
import com.myapps.onlineexaminationapps.ui.teacher.TeacherStudentAnswerScreen
import com.myapps.onlineexaminationapps.ui.teacher.TeacherSubmissionDetailScreen
import com.myapps.onlineexaminationapps.ui.teacher.TeacherSubmissionListScreen

@Composable
fun NavGraph() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {

        // Splash / Auth Check
        composable("splash") {
            SplashScreen(
                onAuthenticated = { role ->
                    navController.navigate("request_location/$role") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onUnauthenticated = {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // Login
        composable("login") {
            LoginScreen(
                onLoginSuccess = { role ->
                    navController.navigate("request_location/$role") {
                        popUpTo("login") {
                            inclusive = true
                        }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate("signup_choice")
                }
            )
        }

        // Student / Teacher choice
        composable("signup_choice") {
            SignUpChoiceScreen(
                onStudentClick = {
                    navController.navigate("student_register")
                },
                onTeacherClick = {
                    navController.navigate("teacher_register")
                },
                onHomeClick = {
                    navController.popBackStack("login", inclusive = false)
                }
            )
        }

        // Student Registration
        composable("student_register") {
            SignUpScreen(
                isTeacher = false,
                onSignUpSuccess = {
                    navController.navigate("request_location/student") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onHomeClick = {
                    navController.popBackStack("login", inclusive = false)
                }
            )
        }

        // Teacher Registration
        composable("teacher_register") {
            SignUpScreen(
                isTeacher = true,
                onSignUpSuccess = {
                    navController.navigate("request_location/teacher") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onHomeClick = {
                    navController.popBackStack("login", inclusive = false)
                }
            )
        }

        // Location Permission Request Route
        composable("request_location/{role}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "student"
            LocationPermissionScreen(
                role = role,
                onPermissionGranted = {
                    val destination = if (role == "teacher") "teacher_dashboard" else "student_home"
                    navController.navigate(destination) {
                        popUpTo("request_location/$role") { inclusive = true }
                    }
                }
            )
        }

        // Student Dashboard (routes: "student_home" & "home")
        composable("student_home") {
            StudentHomeScreen(
                onChapterClick = { chapterId ->
                    navController.navigate("student_chapter/$chapterId")
                },
                onResultClick = { submissionId ->
                    navController.navigate("result/$submissionId")
                },
                onLogoutClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            StudentHomeScreen(
                onChapterClick = { chapterId ->
                    navController.navigate("student_chapter/$chapterId")
                },
                onResultClick = { submissionId ->
                    navController.navigate("result/$submissionId")
                },
                onLogoutClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Student Chapter Screen
        composable("student_chapter/{chapterId}") { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val parentEntry = remember(backStackEntry) { backStackEntry }
            val examViewModel: StudentExamViewModel = viewModel(parentEntry)
            StudentChapterScreen(
                chapterId = chapterId,
                onBackClick = { navController.popBackStack() },
                onNavigateToReview = {
                    navController.navigate("review_answers/$chapterId")
                },
                viewModel = examViewModel
            )
        }

        // Review Answers Screen
        composable("review_answers/{chapterId}") { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("student_chapter/$chapterId")
            }
            val examViewModel: StudentExamViewModel = viewModel(parentEntry)
            ReviewAnswersScreen(
                viewModel = examViewModel,
                onBackClick = { navController.popBackStack() },
                onSubmitSuccess = { submissionId ->
                    navController.navigate("result/$submissionId") {
                        popUpTo("student_home") { inclusive = false }
                    }
                }
            )
        }

        // Exam Result Screen
        composable("result/{submissionId}") { backStackEntry ->
            val submissionId = backStackEntry.arguments?.getString("submissionId") ?: ""
            ResultScreen(
                submissionId = submissionId,
                onBackClick = { navController.popBackStack() },
                onDashboardClick = {
                    navController.popBackStack("student_home", inclusive = false)
                }
            )
        }

        // Student Questions Flow
        composable("student_questions/{chapterId}") { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            StudentQuestionsScreen(
                chapterId = chapterId,
                onBackClick = { navController.popBackStack() },
                onSubmitSuccess = { navController.popBackStack() }
            )
        }

        // Teacher Dashboard Route (teacher_dashboard)
        composable("teacher_dashboard") {
            TeacherDashboardScreen(
                onCreateChapterClick = {
                    navController.navigate("create_chapter")
                },
                onCreateQuestionClick = {
                    navController.navigate("create_question")
                },
                onViewStudentAnswersClick = {
                    navController.navigate("teacher_answers")
                },
                onAnalyticsClick = {
                    navController.navigate("teacher_analytics")
                },
                onChapterClick = { chapterId ->
                    navController.navigate("question_management/$chapterId")
                },
                onLogoutClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Teacher Home Alias Route
        composable("teacher_home") {
            TeacherDashboardScreen(
                onCreateChapterClick = {
                    navController.navigate("create_chapter")
                },
                onCreateQuestionClick = {
                    navController.navigate("create_question")
                },
                onViewStudentAnswersClick = {
                    navController.navigate("teacher_answers")
                },
                onAnalyticsClick = {
                    navController.navigate("teacher_analytics")
                },
                onChapterClick = { chapterId ->
                    navController.navigate("question_management/$chapterId")
                },
                onLogoutClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Teacher Analytics
        composable("teacher_analytics") {
            TeacherAnalyticsScreen(
                onBackClick = { navController.popBackStack() },
                onSubmissionClick = { submissionId ->
                    navController.navigate("teacher_submission/$submissionId")
                }
            )
        }

        // Create Question
        composable("create_question") {
            CreateQuestionScreen(
                preselectedChapterId = null,
                onBackClick = {
                    navController.popBackStack()
                },
                onCreateChapterClick = {
                    navController.navigate("create_chapter")
                },
                onQuestionSaved = {
                    navController.popBackStack()
                }
            )
        }

        // View Student Answers Flow
        composable("view_student_answers") {
            TeacherStudentAnswerScreen(
                onBackClick = { navController.popBackStack() },
                onChapterClick = { chapterId ->
                    navController.navigate("teacher_submissions/$chapterId")
                }
            )
        }

        composable("teacher_answers") {
            TeacherStudentAnswerScreen(
                onBackClick = { navController.popBackStack() },
                onChapterClick = { chapterId ->
                    navController.navigate("teacher_submissions/$chapterId")
                }
            )
        }

        composable("teacher_submissions/{chapterId}") { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            TeacherSubmissionListScreen(
                chapterId = chapterId,
                onBackClick = { navController.popBackStack() },
                onSubmissionClick = { submissionId ->
                    navController.navigate("teacher_submission/$submissionId")
                }
            )
        }

        composable("teacher_submission/{submissionId}") { backStackEntry ->
            val submissionId = backStackEntry.arguments?.getString("submissionId") ?: ""
            TeacherSubmissionDetailScreen(
                submissionId = submissionId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Create Chapter Route
        composable("create_chapter") {
            CreateChapterScreen(
                chapterId = null,
                onBackClick = {
                    navController.popBackStack()
                },
                onChapterSaved = {
                    navController.popBackStack()
                }
            )
        }

        // Edit Chapter Route
        composable("create_chapter?chapterId={chapterId}") { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId")
            CreateChapterScreen(
                chapterId = chapterId,
                onBackClick = {
                    navController.popBackStack()
                },
                onChapterSaved = {
                    navController.popBackStack()
                }
            )
        }

        // Chapter List
        composable("chapter_list") {
            ChapterListScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onChapterClick = { chapterId ->
                    navController.navigate("question_management/$chapterId")
                }
            )
        }

        // Question Management
        composable("question_management/{chapterId}") { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            QuestionManagementScreen(
                chapterId = chapterId,
                onBackClick = {
                    navController.popBackStack()
                },
                onAddQuestionClick = { id ->
                    navController.navigate("add_question/$id")
                },
                onEditChapterClick = { id ->
                    navController.navigate("create_chapter?chapterId=$id")
                },
                onEditQuestionClick = { cId, qId ->
                    navController.navigate("add_question/$cId?questionId=$qId")
                }
            )
        }

        // Add/Edit Question
        composable("add_question/{chapterId}?questionId={questionId}") { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            CreateQuestionScreen(
                preselectedChapterId = chapterId,
                onBackClick = {
                    navController.popBackStack()
                },
                onCreateChapterClick = {
                    navController.navigate("create_chapter")
                },
                onQuestionSaved = {
                    navController.popBackStack()
                }
            )
        }
    }
}
