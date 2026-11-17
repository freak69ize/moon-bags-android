package com.moonbags.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.decode.ImageDecoderDecoder
import com.moonbags.app.ui.MoonRoot
import com.moonbags.app.ui.MoonViewModel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MoonViewModel = viewModel()
            val context = LocalContext.current
            val loader = remember {
                ImageLoader.Builder(context)
                    .components { add(ImageDecoderDecoder.Factory()) }
                    .build()
            }
            CompositionLocalProvider(LocalImageLoader provides loader) {
                MoonRoot(vm)
            }
        }
    }
}
