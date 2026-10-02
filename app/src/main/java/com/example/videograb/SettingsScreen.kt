package com.example.videograb

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val updating by Engine.updating.collectAsState()
    val engine by Engine.state.collectAsState()
    var useLogin by remember { mutableStateOf(Prefs.useLogin) }
    var ytLogin by remember { mutableStateOf(Prefs.youtubeLogin) }
    var nightly by remember { mutableStateOf(Prefs.nightly) }

    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card2 {
                SwitchRow(
                    "Use logins from the Browser tab",
                    "Lets private or login-only videos (Facebook, X, TikTok) download after you sign in to the site in the in-app browser.",
                    useLogin
                ) { useLogin = it; Prefs.useLogin = it }
                SwitchRow(
                    "Also use login for YouTube",
                    "Only needed when YouTube asks you to confirm you\u2019re not a bot. Using an account with downloaders can risk the account being flagged.",
                    ytLogin
                ) { ytLogin = it; Prefs.youtubeLogin = it }
            }

            Card2 {
                Text("Download engine", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (engine) {
                        is EngineState.Starting -> "Starting\u2026"
                        is EngineState.Ready -> "Ready. It refreshes itself about twice a day and whenever a site changes."
                        is EngineState.Failed -> "Not running: ${(engine as EngineState.Failed).message}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwitchRow(
                    "Use nightly builds",
                    "Gets site fixes sooner, but is slightly less tested.",
                    nightly
                ) { nightly = it; Prefs.nightly = it }
                OutlinedButton(
                    enabled = !updating && engine is EngineState.Ready,
                    onClick = {
                        scope.launch {
                            val msg = Engine.update(nightly)
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (updating) "Updating\u2026" else "Update engine now") }
            }

            Card2 {
                Text("Where files go", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Videos: Movies/VideoGrab\nAudio (MP3): Music/VideoGrab",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Only download content you own or have permission to save. Respect each platform\u2019s terms and the rights of creators.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Card2(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
