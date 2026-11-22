package com.axora.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.axora.app.R
import com.axora.app.domain.model.ChatMessage
import com.axora.app.ui.components.ImageViewerDialog
import com.axora.app.ui.viewmodel.ChatViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val images by viewModel.galleryImages.collectAsState()
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var pendingSave by remember { mutableStateOf<ChatMessage?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    val noStoragePermission = stringResource(R.string.no_storage_permission)
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val message = pendingSave
        pendingSave = null
        if (granted && message != null) performSave(context, scope, message)
        else Toast.makeText(context, noStoragePermission, Toast.LENGTH_SHORT).show()
    }

    fun saveImage(message: ChatMessage) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = message
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        performSave(context, scope, message)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.gallery_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurfaceVariant
                )
            )
        },
        containerColor = scheme.background
    ) { padding ->
        if (images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(images, key = { it.id }) { message ->
                    GalleryThumb(message = message, onClick = {
                        val index = images.indexOf(message)
                        if (index >= 0) viewerIndex = index
                    })
                }
            }
        }
    }

    viewerIndex?.let { index ->
        ImageViewerDialog(
            messages = images,
            initialIndex = index,
            onDismiss = { viewerIndex = null },
            onSave = { saveImage(it) },
            onShare = { shareMedia(context, it) }
        )
    }
}

@Composable
private fun GalleryThumb(message: ChatMessage, onClick: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val file = File(message.imageUrl.orEmpty())

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(enabled = file.exists(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (file.exists()) {
            val model = ImageRequest.Builder(context)
                .data(file)
                .size(240)
                .crossfade(true)
                .build()
            Image(
                painter = rememberAsyncImagePainter(model),
                contentDescription = stringResource(R.string.cd_generated_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.ImageNotSupported,
                contentDescription = stringResource(R.string.file_unavailable),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
