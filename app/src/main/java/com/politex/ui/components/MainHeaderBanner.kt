package com.politex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.ColorBgMain

@Composable
fun MainHeaderBanner(modifier: Modifier = Modifier) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(ColorBgMain),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(ColorBgMain)
                .statusBarsPadding()
                .height(56.dp),
        ) {
            CollegeLogo(
                modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .height(44.dp)
                    .fillMaxWidth(),
            )
        }

        AppDivider()
    }
}
