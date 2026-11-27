package com.slot.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slot.app.core.design.SlotTheme
import com.slot.app.core.design.Tokens

@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s6),
        ) {
            Spacer(Modifier.height(Tokens.Space.s10))

            Text(
                text = "PAULPLAN · PHASE 0",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )

            Text(
                text = "A quieter schedule.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "Compose is wired to the same generated tokens as the web app. No screens yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(Tokens.Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Text(
                        text = "Subject tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        listOf(
                            Tokens.Tag.Light.indigo,
                            Tokens.Tag.Light.sky,
                            Tokens.Tag.Light.violet,
                            Tokens.Tag.Light.amber,
                            Tokens.Tag.Light.rose,
                            Tokens.Tag.Light.emerald,
                        ).forEach { color -> TagDot(color) }
                    }
                }
            }

            Button(onClick = {}) {
                Text("Primary action", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TagDot(color: Color) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
    SlotTheme {
        PlaceholderScreen()
    }
}
