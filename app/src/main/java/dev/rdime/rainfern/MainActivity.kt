package dev.rdime.rainfern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.rdime.rainfern.ui.RainfernApp
import dev.rdime.rainfern.ui.RainfernViewModel
import dev.rdime.rainfern.ui.RainfernViewModelFactory
import dev.rdime.rainfern.ui.theme.RainfernTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RainfernViewModel by viewModels {
        RainfernViewModelFactory(
            repository = (application as RainfernApplication).container.weatherRepository,
            placesRepository = (application as RainfernApplication).container.placesRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RainfernTheme {
                RainfernApp(viewModel = viewModel)
            }
        }
    }
}
