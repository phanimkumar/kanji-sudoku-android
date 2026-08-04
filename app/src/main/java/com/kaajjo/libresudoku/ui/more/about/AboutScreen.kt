package com.kaajjo.libresudoku.ui.more.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaajjo.libresudoku.BuildConfig
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.destinations.AboutLibrariesScreenDestination
import com.kaajjo.libresudoku.ui.components.AnimatedNavigation
import com.kaajjo.libresudoku.ui.theme.ColorUtils.harmonizeWithPrimary
import com.kaajjo.libresudoku.ui.theme.icons.LibreSudokuIconAlt
import com.kaajjo.libresudoku.util.FlavorUtil
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.Language
@Destination(style = AnimatedNavigation::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navigator.popBackStack()
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                R.drawable.ic_round_arrow_back_24
                            ),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            ) {
                Icon(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(48.dp),
                    imageVector = Icons.Rounded.LibreSudokuIconAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "Developed by Sudoku Multiverse",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            ) {
                Text(
                    text = stringResource(
                        R.string.app_version,
                        BuildConfig.VERSION_NAME +
                                if (FlavorUtil.isFoss()) "-FOSS" else "",
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        vertical = 5.dp,
                        horizontal = 10.dp
                    )
                )
            }

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            WebsiteCard(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://sudokumultiverse.com")
                        )
                    )
                }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )


            LicensesCard(
                onClick = {
                    navigator.navigate(
                        AboutLibrariesScreenDestination()
                    )
                }
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "Kanji Sudoku is based on the open-source LibreSudoku project and has been modified and extended by Sudoku Multiverse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "© 2026 Sudoku Multiverse",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
private fun LicensesCard(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
                .harmonizeWithPrimary(),
            modifier = Modifier.size(28.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(
                    R.string.libraries_licenses
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = stringResource(
                    R.string.libraries_licenses_title
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WebsiteCard(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Icon(
            imageVector = Icons.Outlined.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = "Website",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "sudokumultiverse.com",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}