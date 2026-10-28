package com.gimirick.circletosearch

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.gimirick.circletosearch.data.MAX_BALL_SIZE_DP
import com.gimirick.circletosearch.data.MIN_BALL_SIZE_DP
import com.gimirick.circletosearch.ui.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen(this) }
    }

    fun accessibilityEnabled(): Boolean = runCatching {
        val enabled = Secure.getString(contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val service = ComponentName(this, BallAccessibilityService::class.java)
        enabled.split(':').any { ComponentName.unflattenFromString(it) == service }
    }.getOrDefault(false)

    fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun SettingsScreen(activity: MainActivity, model: SettingsViewModel = viewModel()) {
    val settings by model.settings.collectAsState()
    var serviceEnabled by remember { mutableStateOf(activity.accessibilityEnabled()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = activity.accessibilityEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val widthClass = calculateWindowSizeClass(activity).widthSizeClass

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF526EFF),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE8EDFF),
            onPrimaryContainer = Color(0xFF10205F),
        )
    ) {
        Scaffold(topBar = { TopAppBar(title = { Text("Circle to Search") }) }) { insets ->
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                val wide = widthClass == WindowWidthSizeClass.Expanded
                if (wide) {
                    Row(
                        Modifier.width(maxWidth.coerceAtMost(1120.dp)).padding(horizontal = 32.dp, vertical = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            WelcomeCard(serviceEnabled, activity::openAccessibilitySettings)
                            InfoCard()
                        }
                        Column(Modifier.weight(1f)) { ControlsCard(settings.ballEnabled, settings.ballSizeDp, model) }
                    }
                } else {
                    Column(
                        Modifier.width(maxWidth.coerceAtMost(680.dp)).padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        WelcomeCard(serviceEnabled, activity::openAccessibilitySettings)
                        ControlsCard(settings.ballEnabled, settings.ballSizeDp, model)
                        InfoCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(enabled: Boolean, openSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (enabled) "Your search ball is ready" else "Enable the floating search ball", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Circle anything on your screen to send just that area to Google Lens.", style = MaterialTheme.typography.bodyLarge)
            if (enabled) {
                Text("Accessibility service enabled", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            } else {
                Button(onClick = openSettings, modifier = Modifier.height(48.dp)) { Text("Open accessibility settings") }
            }
        }
    }
}

@Composable
private fun ControlsCard(enabled: Boolean, sizeDp: Int, model: SettingsViewModel) {
    var previewSize by remember(sizeDp) { mutableStateOf(sizeDp.toFloat()) }
    Card {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Floating ball", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    val previewBallSize = previewSize.toInt()
                    Box(
                        Modifier.size(previewBallSize.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0x22000000), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painterResource(R.drawable.ic_circle_to_search),
                            contentDescription = "Floating ball preview",
                            modifier = Modifier.size((previewBallSize * BALL_ICON_SCALE).toInt().dp)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Show ball", style = MaterialTheme.typography.titleMedium)
                    Text(if (enabled) "Tap to capture the screen" else "Ball is hidden", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = model::setBallEnabled)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ball size", style = MaterialTheme.typography.titleMedium)
                Text("${previewSize.toInt()} dp", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = previewSize,
                onValueChange = { previewSize = it },
                onValueChangeFinished = { model.setBallSize(previewSize.toInt()) },
                valueRange = MIN_BALL_SIZE_DP.toFloat()..MAX_BALL_SIZE_DP.toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${MIN_BALL_SIZE_DP} dp", style = MaterialTheme.typography.labelMedium)
                Text("${MAX_BALL_SIZE_DP} dp", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Why accessibility access?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Android requires an accessibility service to place the ball over other apps and capture the screen when you ask it to. This service requests screenshot capture only; it does not read screen text or window content. Images stay in memory until you draw a selection.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

