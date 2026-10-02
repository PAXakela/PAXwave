package com.music.bitchord.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.R
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SmokeShot {
    @Test
    fun smoke() {
        captureRoboImage("build/shots/smoke.png") {
            BitChordTheme(darkTheme = true) {
                Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    Icon(painterResource(R.drawable.ic_logo), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onBackground)
                    Text("PAXwave Weiterhören", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
