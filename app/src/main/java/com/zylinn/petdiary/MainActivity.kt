package com.zylinn.petdiary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zylinn.petdiary.ui.PetDiaryApp
import com.zylinn.petdiary.ui.theme.PetDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PetDiaryTheme {
                PetDiaryApp()
            }
        }
    }
}
