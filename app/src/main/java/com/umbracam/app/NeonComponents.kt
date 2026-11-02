package com.umbracam.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun NeonGround(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(NeonBackground)) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width*.25f,size.height*.13f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF6320BA).copy(alpha=.23f),Color.Transparent), center, size.width*.9f),size.width*.9f,center)
            val wave = Path().apply {
                moveTo(-size.width*.1f,size.height*.97f)
                cubicTo(size.width*.3f,size.height*.85f,size.width*.72f,size.height*.99f,size.width*1.1f,size.height*.78f)
            }
            listOf(48f to .015f,32f to .025f,16f to .05f,8f to .08f,2f to .2f,.7f to .5f).forEach { (width,alpha) ->
                drawPath(wave,Color(0xFFB52BFF).copy(alpha=alpha),style=Stroke(width.dp.toPx()))
            }
        }
        content()
    }
}

@Composable
fun SuiteIcon(resource: Int, description: String? = null, modifier: Modifier = Modifier.size(24.dp)) {
    Icon(painterResource(resource), description, modifier, tint=LocalContentColor.current)
}

@Composable
fun NeonButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = if(enabled) listOf(Color(0xFFE878FF),Color(0xFF9D18EB),Color(0xFF6600B8)) else listOf(NeonPanelColor,NeonPanelColor)
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(colors))
        .border(1.dp,if(enabled) Color(0xFFD9A0FF) else NeonOutline,RoundedCornerShape(24.dp))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).heightIn(min=48.dp).padding(horizontal=22.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
        Text(label,style=MaterialTheme.typography.labelLarge,color=if(enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NeonPanel(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(Color(0xFF282235),Color(0xFF15121E))))
        .border(1.dp,NeonOutline,RoundedCornerShape(20.dp)).padding(contentPadding),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}

@Composable
fun RecordAction(label: String, recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.widthIn(min=104.dp,max=180.dp).clip(RoundedCornerShape(24.dp)).clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(8.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val colors = if(enabled) listOf(Color(0xFFFF7586),Color(0xFFFF203F),Color(0xFFC80032)) else listOf(NeonPanelColor,NeonPanelColor)
        Box(Modifier.size(74.dp).border(3.dp,if(enabled) Color(0xFFF6E6FF) else NeonOutline,CircleShape).padding(6.dp)
            .background(Brush.verticalGradient(colors),CircleShape),contentAlignment=Alignment.Center) {
            if(recording) Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
        }
        Text(label,style=MaterialTheme.typography.labelMedium,color=if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
