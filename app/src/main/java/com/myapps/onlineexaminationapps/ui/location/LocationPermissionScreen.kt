package com.myapps.onlineexaminationapps.ui.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

fun isLocationPermissionGranted(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

fun markLocationPermissionHandled(context: Context) {
    val prefs = context.getSharedPreferences("location_permission_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("permission_handled", true).apply()
}

fun isLocationPermissionHandled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("location_permission_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("permission_handled", false)
}

fun shouldSkipLocationScreen(context: Context): Boolean {
    return isLocationPermissionGranted(context) || isLocationPermissionHandled(context)
}

fun getPostLoginDestination(context: Context, role: String): String {
    if (shouldSkipLocationScreen(context)) {
        return if (role.equals("teacher", ignoreCase = true)) "teacher_dashboard" else "student_dashboard"
    }
    return "request_location/$role"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPermissionScreen(
    role: String,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    var isPermissionGranted by remember { mutableStateOf(isLocationPermissionGranted(context)) }
    var hasRequestedPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val granted = fineGranted || coarseGranted

        hasRequestedPermission = true
        isPermissionGranted = granted

        markLocationPermissionHandled(context)

        if (granted) {
            Log.d("LOCATION_PERMISSION_DEBUG", "Location permission GRANTED for UID: ${currentUser?.uid}, Role: $role")
        } else {
            Log.d("LOCATION_PERMISSION_DEBUG", "Location permission DENIED for UID: ${currentUser?.uid}, Role: $role")
        }
        onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        val uid = currentUser?.uid ?: "unknown"
        Log.d("LOCATION_PERMISSION_DEBUG", "Checking location permission. UID: $uid, Role: $role")

        if (shouldSkipLocationScreen(context)) {
            Log.d("LOCATION_PERMISSION_DEBUG", "Location permission ALREADY GRANTED or HANDLED for UID: $uid, Role: $role. Skipping request.")
            onPermissionGranted()
        } else {
            Log.d("LOCATION_PERMISSION_DEBUG", "Location permission status: MISSING. Launching system request for UID: $uid, Role: $role")
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Permission") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isPermissionGranted && hasRequestedPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Location Permission Required",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Location permission is required for this application.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                Log.d("LOCATION_PERMISSION_DEBUG", "Retrying location permission request for UID: ${currentUser?.uid}")
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission / Retry")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                Log.d("LOCATION_PERMISSION_DEBUG", "Opening App Settings for UID: ${currentUser?.uid}")
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open App Settings")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                Log.d("LOCATION_PERMISSION_DEBUG", "User selected Continue Without Location for UID: ${currentUser?.uid}")
                                markLocationPermissionHandled(context)
                                onPermissionGranted()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue Without Location")
                        }
                    }
                }
            } else if (isPermissionGranted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Permission granted. Redirecting...")
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Checking location permission...")
                }
            }
        }
    }
}
