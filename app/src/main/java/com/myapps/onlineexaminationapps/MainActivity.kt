package com.myapps.onlineexaminationapps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myapps.onlineexaminationapps.ui.navigation.NavGraph
import com.myapps.onlineexaminationapps.ui.theme.OnlineExaminationAppsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnlineExaminationAppsTheme {
                NavGraph()
            }
        }
    }
}
