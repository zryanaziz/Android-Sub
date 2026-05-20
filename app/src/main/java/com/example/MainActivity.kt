package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.SubtitleRepository
import com.example.ui.SubtitleViewModel
import com.example.ui.SubtitleViewModelFactory
import com.example.ui.SubtitleWorkspaceScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room and MVVM Architectural Injection hook
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SubtitleRepository(database.subtitleDao())
        val viewModelFactory = SubtitleViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[SubtitleViewModel::class.java]

        setContent {
            MyApplicationTheme {
                SubtitleWorkspaceScreen(viewModel = viewModel)
            }
        }
    }
}
