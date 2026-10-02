package com.music.bitchord.ui.pax

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.R
import com.music.bitchord.ui.components.PAGE_GUTTER

/** Where PAXwave's source lives — what the GPL promises everyone who gets the app. */
const val PAXWAVE_SOURCE_URL = "https://github.com/PAXakela/PAXwave"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** "PAXwave", written as the logo writes it: PAX heavy, wave light. */
@Composable
fun PaxwaveWordmark(size: androidx.compose.ui.unit.TextUnit = 30.sp, color: Color = MaterialTheme.colorScheme.onBackground) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.W800)) { append("PAX") }
            withStyle(SpanStyle(fontWeight = FontWeight.W400)) { append("wave") }
        },
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = size, letterSpacing = (-0.3).sp),
        color = color,
    )
}

/** The entry at the foot of Settings. */
@Composable
fun AboutPaxwaveCard(version: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .padding(top = 24.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { open = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Color.White, modifier = Modifier.size(width = 26.dp, height = 30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pax_about_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(R.string.pax_about_subtitle, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) AboutPaxwaveScreen(version = version, onClose = { open = false })
}

private data class Credit(val name: String, val by: String, val license: String, val url: String?)

private val BASED_ON_CONTRIBUTORS = listOf(
    "kushagrasinghx", "Galaxyyss", "NeoTurcios", "hongducdev", "kmmiio99o", "byburak3525", "penetratorcwl",
    "nitinbhat972", "AbhiTheModder", "AngelCas04", "MaverickRox", "aryasarukkai", "Eful97", "chaudharyjatin115",
    "bazilbycom", "BernoldM", "Elitenotavailable", "InfantAjith96", "SHUBH-snippet", "yxyydev",
)

private val COMPONENTS = listOf(
    Credit("BitChord", "Kushagra Singh & contributors", "GPL-3.0", "https://github.com/kushagrasinghx/BitChord"),
    Credit("Echo Music (glass navigation bar)", "EchoMusicApp", "GPL-3.0", "https://github.com/EchoMusicApp/Echo-Music"),
    Credit("Convx (overscroll, addon runtime)", "cosmictaserdev-creator", "GPL-3.0", "https://github.com/cosmictaserdev-creator/Convx"),
    Credit("Orchard (track analysis)", "SFG545", "AGPL-3.0", "https://github.com/SFG5453/Orchard"),
    Credit("decent-player (USB audio)", "Ma145", "MIT", "https://github.com/Ma145/decent-player"),
    Credit("backdrop (liquid glass)", "Kyant0", "Apache-2.0", "https://github.com/Kyant0/backdrop"),
    Credit("compose-floating-tab-bar", "Elyes Mansour", "Apache-2.0", "https://github.com/elyesmansour/compose-floating-tab-bar"),
    Credit("NewPipe Extractor", "Team NewPipe", "GPL-3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    Credit("InnerTubeX", "Metrolist", "GPL-3.0", "https://github.com/MetrolistGroup"),
    Credit("Kizzy (Discord presence)", "dead8309", "GPL-3.0", "https://github.com/dead8309/Kizzy"),
    Credit("am-lyrics (lyrics animation)", "binimum", "MPL-2.0", "https://github.com/binimum/am-lyrics"),
    Credit("Beat This! (beat tracking model)", "CP JKU", "MIT", "https://github.com/CPJKU/beat_this"),
    Credit("Open-Unmix (vocals model)", "sigsep", "MIT", "https://github.com/sigsep/open-unmix-pytorch"),
    Credit("AndroidX Media3 / ExoPlayer", "Google", "Apache-2.0", "https://github.com/androidx/media"),
    Credit("Jetpack Compose & AndroidX", "Google", "Apache-2.0", "https://developer.android.com/jetpack"),
    Credit("Kotlin & kotlinx", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
    Credit("OkHttp", "Square", "Apache-2.0", "https://square.github.io/okhttp/"),
    Credit("Ktor", "JetBrains", "Apache-2.0", "https://ktor.io"),
    Credit("Coil", "Coil contributors", "Apache-2.0", "https://coil-kt.github.io/coil/"),
    Credit("Haze", "Chris Banes", "Apache-2.0", "https://github.com/chrisbanes/haze"),
    Credit("ONNX Runtime", "Microsoft", "MIT", "https://onnxruntime.ai"),
    Credit("Mozilla Rhino", "Mozilla", "MPL-2.0", "https://github.com/mozilla/rhino"),
    Credit("QuickJS-kt", "dokar3", "Apache-2.0", "https://github.com/dokar3/quickjs-kt"),
    Credit("nanojson", "Team NewPipe / mmastrac", "MIT / Apache-2.0", "https://github.com/TeamNewPipe/nanojson"),
    Credit("BgUtils (po_token patterns)", "LuanRT", "MIT", "https://github.com/LuanRT/BgUtils"),
    Credit("jsoup", "Jonathan Hedley", "MIT", "https://jsoup.org"),
    Credit("SMBJ", "Hierynomus", "Apache-2.0", "https://github.com/hierynomus/smbj"),
    Credit("ZXing", "ZXing authors", "Apache-2.0", "https://github.com/zxing/zxing"),
    Credit("compose-richtext", "halilozercan", "Apache-2.0", "https://github.com/halilozercan/compose-richtext"),
    Credit("Inter (typeface)", "Rasmus Andersson", "OFL-1.1", "https://rsms.me/inter/"),
    Credit("Protocol Buffers", "Google", "BSD-3-Clause", "https://protobuf.dev"),
    Credit("JSR-305 annotations", "FindBugs", "BSD-3-Clause", "https://code.google.com/archive/p/jsr-305/"),
)

private val SERVICES = listOf(
    Credit("Radio Browser", "community radio directory", "radio-browser.info", "https://www.radio-browser.info"),
    Credit("Apple Podcasts directory", "podcast search & charts", "Apple Inc.", "https://podcasts.apple.com"),
    Credit("Podcast feeds", "© their creators", "RSS", null),
    Credit("YouTube Music", "streams & catalogue", "Google LLC — not affiliated", "https://music.youtube.com"),
)

/** About PAXwave: what it is, its licence, and everyone whose work it is built on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutPaxwaveScreen(version: String, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    var showLicense by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                item {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Text(stringResource(R.string.pax_about_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF111111), Color.Black))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Color.White, modifier = Modifier.size(width = 60.dp, height = 69.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        PaxwaveWordmark()
                        Text(
                            stringResource(R.string.pax_version, version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.pax_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
                item {
                    AboutCard {
                        Text(stringResource(R.string.pax_free_software_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.pax_gpl_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        AboutLink(Icons.Rounded.Description, stringResource(R.string.pax_read_license)) { showLicense = true }
                        AboutLink(Icons.Rounded.Code, stringResource(R.string.pax_source_code)) { uri.openUri(PAXWAVE_SOURCE_URL) }
                        AboutLink(Icons.Rounded.Description, "gnu.org/licenses/gpl-3.0") { uri.openUri(GPL_URL) }
                    }
                }
                item {
                    AboutCard {
                        Text(stringResource(R.string.pax_based_on_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.pax_based_on_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BASED_ON_CONTRIBUTORS.forEach { name ->
                                Text(
                                    "@$name",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.background)
                                        .clickable { uri.openUri("https://github.com/$name") }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                item { AboutHeading(stringResource(R.string.pax_components_title)) }
                items(COMPONENTS) { CreditRow(it) { url -> uri.openUri(url) } }
                item { AboutHeading(stringResource(R.string.pax_services_title)) }
                items(SERVICES) { CreditRow(it) { url -> uri.openUri(url) } }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pax_thanks), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showLicense) LicenseDialog(onClose = { showLicense = false })
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = PAGE_GUTTER + 6.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun AboutHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = PAGE_GUTTER + 6.dp, end = PAGE_GUTTER, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun AboutLink(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CreditRow(credit: Credit, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (credit.url != null) Modifier.clickable { onOpen(credit.url) } else Modifier)
            .padding(horizontal = PAGE_GUTTER + 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(credit.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(credit.by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            credit.license,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The full GNU GPL v3, as shipped inside the app. */
@Composable
private fun LicenseDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }
            .getOrDefault("GNU General Public License v3.0 — https://www.gnu.org/licenses/gpl-3.0.html")
    }
    val paragraphs = remember(text) { text.split(Regex("\n\\s*\n")) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                item {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Text("GPL-3.0", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                items(paragraphs) { para ->
                    Text(
                        para.trim(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
