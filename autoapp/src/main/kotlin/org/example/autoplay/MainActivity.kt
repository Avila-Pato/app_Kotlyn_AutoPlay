package org.example.autoplay

import android.content.res.Configuration
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
import org.example.autoplay.car.NavigationMode
import org.example.autoplay.phone.PhoneClient
import org.example.autoplay.phone.PhoneScreen
import org.example.autoplay.phone.PhoneStatus
import org.example.autoplay.ui.AutoPlayTheme
import org.example.autoplay.ui.HomeScreen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        NavigationMode.onFloatingChanged(isInPictureInPictureMode)
    }

    @Inject lateinit var carServer: CarServer
    @Inject lateinit var phoneClient: PhoneClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so system bar icons must be light regardless of the system theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // A session kept running in the background reopens straight into it; otherwise the
        // device's role does, and only an undecided device sees the home screen
        val role = DeviceRoles.resolve(this)
        val start = when {
            carServer.state.value.running -> "car"
            phoneClient.status.value != PhoneStatus.Disconnected -> "phone"
            role == DeviceRole.Car -> "car"
            role == DeviceRole.Phone -> "phone"
            else -> "home"
        }
        setContent {
            AutoPlayTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    // A device that knows its role has nothing to go back to: leaving closes the app
                    val leave: () -> Unit = {
                        if (DeviceRoles.resolve(this) != null) {
                            finish()
                        } else if (!nav.popBackStack("home", inclusive = false)) {
                            nav.navigate("home") { popUpTo(0) }
                        }
                    }
                    val switchTo: (DeviceRole) -> Unit = { newRole ->
                        DeviceRoles.save(this, newRole)
                        nav.navigate(if (newRole == DeviceRole.Car) "car" else "phone") { popUpTo(0) }
                    }
                    NavHost(nav, startDestination = start, modifier = Modifier.safeDrawingPadding()) {
                        composable("home") {
                            HomeScreen(onCar = { switchTo(DeviceRole.Car) }, onPhone = { switchTo(DeviceRole.Phone) })
                        }
                        composable("car") { CarScreen(onBack = leave, onUseAsPhone = { switchTo(DeviceRole.Phone) }) }
                        composable("phone") { PhoneScreen(onBack = leave, onUseAsCar = { switchTo(DeviceRole.Car) }) }
                    }
                }
            }
        }
    }
}
