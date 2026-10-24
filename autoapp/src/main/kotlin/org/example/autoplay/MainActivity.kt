package org.example.autoplay

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.example.autoplay.car.CarScreen
import org.example.autoplay.car.CarServer
import org.example.autoplay.phone.PhoneClient
import org.example.autoplay.phone.PhoneScreen
import org.example.autoplay.phone.PhoneStatus
import org.example.autoplay.ui.AutoPlayTheme
import org.example.autoplay.ui.HomeScreen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var carServer: CarServer
    @Inject lateinit var phoneClient: PhoneClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so system bar icons must be light regardless of the system theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // A session kept running in the background: reopen straight into it
        val start = when {
            carServer.state.value.running -> "car"
            phoneClient.status.value != PhoneStatus.Disconnected -> "phone"
            else -> "home"
        }
        setContent {
            AutoPlayTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    // Works whether home is below in the stack or the app opened straight into a session
                    val goHome: () -> Unit = {
                        if (!nav.popBackStack("home", inclusive = false)) {
                            nav.navigate("home") { popUpTo(0) }
                        }
                    }
                    NavHost(nav, startDestination = start, modifier = Modifier.safeDrawingPadding()) {
                        composable("home") {
                            HomeScreen(onCar = { nav.navigate("car") }, onPhone = { nav.navigate("phone") })
                        }
                        composable("car") { CarScreen(onBack = goHome) }
                        composable("phone") { PhoneScreen(onBack = goHome) }
                    }
                }
            }
        }
    }
}
