package com.dahham.jobwriter

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.*

val CAMERA_ZOOM_LEVEL = "CAMERA_ZOOM_LEVEL"

@ExperimentalMaterialApi
@Composable
fun RecentJobsView(
    paddingValues: PaddingValues,
    all: List<Job>?,
    onContinueJobClicked: (job: Job) -> Unit
) {


    if (all == null || all.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f), elevation = 12.dp
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "No data currently available",
                        textAlign = TextAlign.Center
                    )
                }

            }
        }

    } else {
        LazyColumn(contentPadding = paddingValues) {

            itemsIndexed(
                items = all,
                key = { index, item -> return@itemsIndexed item.uid.toString() + item.w1.toString() + item.w2.toString() + item.w3.toString() }) { index, job ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    elevation = 6.dp
                ) {
                    JobView(job = job, onContinueJobClicked = onContinueJobClicked)

                    if (index != all.size) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun JobView(job: Job, onContinueJobClicked: (job: Job) -> Unit) {
    val date = remember {
        SimpleDateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.MEDIUM,
            Locale.getDefault()
        ).format(job.date).toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ID: ${job.uid} -  (${job.lastJobOperator.name})",
                color = MaterialTheme.colors.primaryVariant,
                fontWeight = FontWeight.Bold
            )
            Text(text = date, color = MaterialTheme.colors.secondaryVariant)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.5f)) {
                Text(text = "First  Weight: ${job.w1}", fontSize = 12.sp)
                Text(text = "Second Weight: ${job.w2}", fontSize = 12.sp)
                Text(text = "Third  Weight: ${job.w3}", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Result: ${
                        try {
                            job.calculate()
                        } catch (ex: ArithmeticException) {
                            "0"
                        }
                    }",
                    fontSize = 16.sp,
                    color = MaterialTheme.colors.primaryVariant
                )
            }

            OutlinedButton(onClick = {
                onContinueJobClicked(job)
            }) {
                Text(text = "Continue Job", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }

}

@Composable
fun CameraView(modifier: Modifier) {
    val contextAmbient = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraView = PreviewView(mainActivityUtils.getContext())
    var camera: Camera? = null

    val cameraProvider = remember {
        ProcessCameraProvider.getInstance(contextAmbient)
    }
    val cameraZoom = rememberSaveable {
        mutableStateOf(mainActivityUtils.getSharedPreference().getFloat(CAMERA_ZOOM_LEVEL, 0.5f) ?: 0.5f)
    }

    var hasInitCamera by remember{
        mutableStateOf(true)
    }

    DisposableEffect(key1 = cameraProvider) {
        cameraView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        cameraProvider.addListener({
            val cameraProviderInner = cameraProvider.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraView.surfaceProvider)
            }

            try {
                cameraProviderInner.unbindAll()
                camera = cameraProviderInner.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )

                if (mainActivityUtils.getViewModel().isLegacyMode.value == true) {
                    camera?.cameraControl?.setLinearZoom(cameraZoom.value)
                }

            } catch (ex: Exception) {
                hasInitCamera = false
            }

            mainActivityUtils.cameraInitialize(cameraView, camera)
        }, ContextCompat.getMainExecutor(mainActivityUtils.getContext()))

        onDispose {
            cameraProvider.cancel(true)
            hasInitCamera = false
        }

    }

    _CameraView(hasInitCamera, cameraView, modifier)
}

@Composable
private fun _CameraView(
    hasInitCamera: Boolean,
    cameraView: PreviewView,
    modifier: Modifier
) {
    if (hasInitCamera) {
        AndroidView(factory = {
            cameraView
        }, modifier = modifier)
    }
    else {
        AndroidView(factory = {
            LayoutInflater.from(it)
                .inflate(R.layout.camera_not_initialized_error, null, false)
        }, modifier = modifier)
    }

}


