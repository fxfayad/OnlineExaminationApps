# Implementation Plan - Login Page

This plan covers the creation of a professional login page using Jetpack Compose, including UI components, a ViewModel for state management, and basic navigation setup.

## User Review Required

> [!IMPORTANT]
> **Authentication Backend**: This plan implements the UI and ViewModel logic. I noticed a `google-services.json` file in your project, but Firebase dependencies are not yet added to your `build.gradle` files. Would you like me to include Firebase Authentication integration as part of this task?

> [!NOTE]
> I will be using **Jetpack Compose Material 3** for the UI, as it's already configured in your project.

## Proposed Changes

### Dependencies

#### [MODIFY] [libs.versions.toml](file:///D:/CODE/OnlineExaminationApps/gradle/libs.versions.toml)
- Add versions and libraries for:
    - Navigation Compose
    - Lifecycle ViewModel Compose
    - (Optional) Firebase BOM and Auth (pending user confirmation)

#### [MODIFY] [app/build.gradle.kts](file:///D:/CODE/OnlineExaminationApps/app/build.gradle.kts)
- Add the new dependencies to the `dependencies` block.

---

### UI Components

#### [NEW] [LoginScreen.kt](file:///D:/CODE/OnlineExaminationApps/app/src/main/java/com/myapps/onlineexaminationapps/ui/login/LoginScreen.kt)
- Create a Composable `LoginScreen` containing:
    - App Logo/Title
    - Email `OutlinedTextField`
    - Password `OutlinedTextField` (with visibility toggle)
    - "Login" Button
    - "Forgot Password?" and "Sign Up" text links
- Implement UI state handling (loading, error messages).

#### [NEW] [LoginViewModel.kt](file:///D:/CODE/OnlineExaminationApps/app/src/main/java/com/myapps/onlineexaminationapps/ui/login/LoginViewModel.kt)
- Create `LoginViewModel` to handle:
    - Email and password input state.
    - Input validation (e.g., non-empty, valid email format).
    - Login logic placeholder (or Firebase Auth integration if requested).

---

### Navigation & Integration

#### [NEW] [NavGraph.kt](file:///D:/CODE/OnlineExaminationApps/app/src/main/java/com/myapps/onlineexaminationapps/ui/navigation/NavGraph.kt)
- Define navigation routes (e.g., `login`, `home`).
- Set up `NavHost`.

#### [MODIFY] [MainActivity.kt](file:///D:/CODE/OnlineExaminationApps/app/src/main/java/com/myapps/onlineexaminationapps/MainActivity.kt)
- Integrate the `NavHost` into the `setContent` block.

## Verification Plan

### Automated Tests
- I will create a basic UI test for the Login screen to verify that input fields and the login button are present and clickable.

### Manual Verification
- Use **Compose Preview** to verify the look and feel of `LoginScreen`.
- Run the app to verify navigation and input handling.
