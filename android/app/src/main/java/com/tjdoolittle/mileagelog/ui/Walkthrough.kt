package com.tjdoolittle.mileagelog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tjdoolittle.mileagelog.data.Store

/** First-launch walkthrough. Shown full screen until finished or skipped; Settings can show it again. */
@Composable
fun Walkthrough(onDone: () -> Unit) {
    val p = LocalPalette.current
    val pages = Store.WALKTHROUGH
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = page == pages.lastIndex
    BackHandler(enabled = page > 0) { page-- }

    Column(
        Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()
            .pointerInput(page) {
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                    if (dx < -80 && page < pages.lastIndex) page++
                    if (dx > 80 && page > 0) page--
                }) { _, d -> dx += d }
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TAX MILEAGE TRACKER", style = Type.label, color = p.signText, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text("Skip", style = Type.bodyBold, color = p.muted) }
        }
        Spacer(Modifier.weight(0.5f))
        AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page",
            modifier = Modifier.fillMaxWidth().weight(4f)) { i ->
            val (title, body) = pages[i]
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Highway-sign style step marker.
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(p.sign).border(2.dp, p.signInk.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("STEP ${i + 1} OF ${pages.size}", style = Type.label.copy(fontSize = 14.sp), color = p.signInk)
                }
                Text(title, style = Type.title.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold), color = p.ink)
                Text(body, style = Type.body.copy(fontSize = 18.sp, lineHeight = 27.sp), color = p.ink, modifier = Modifier.widthIn(max = 560.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(width = if (i == page) 22.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape).background(if (i == page) p.sign else p.line)
                        .clickable { page = i },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) BigButton("Back", Modifier.width(110.dp), kind = "outline") { page-- }
            BigButton(if (last) "Get started" else "Next", Modifier.weight(1f)) { if (last) onDone() else page++ }
        }
        Spacer(Modifier.height(8.dp))
    }
}
