package com.dahham.jobwriter

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.lifecycle.*
import androidx.room.*
import com.dahham.jobwriter.ui.theme.JugWriterTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface MainActivityUtils {

    fun cameraInitialize(cameraView: PreviewView?, camera: Camera?);

    fun getContext(): Context;

    fun getSharedPreference(): SharedPreferences;

    fun getViewModel(): MainActivityViewModel;

    fun getCamera(): Camera?;

    fun getCameraView(): PreviewView?;
}

lateinit var mainActivityUtils: MainActivityUtils

class MainActivity : ComponentActivity(), MainActivityUtils {

    init {
        mainActivityUtils = this
    }

    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val CAMERA_PREFERRED_STATE = "CAMERA_PREFERRED_STATE"

    private var cameraView: PreviewView? = null
    private lateinit var cameraExecutors: ExecutorService
    private var camera: Camera? = null
    private var pref: SharedPreferences? = null
    private var mainActivityViewModel: MainActivityViewModel? = null

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if (pref == null) {
            pref = getSharedPreferences("CAMERA_PREF", 0)
        }

        if (this::cameraExecutors.isInitialized.not()) {
            cameraExecutors = Executors.newSingleThreadExecutor()
        }


        if (mainActivityViewModel == null){
            mainActivityViewModel = MainActivityViewModelFactory(this).create(MainActivityViewModel::class.java)
        }

        setContent {
            val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()

            val bStateTitle =
                remember(key1 = bottomSheetScaffoldState.bottomSheetState.isExpanded) {
                    return@remember if (bottomSheetScaffoldState.bottomSheetState.isExpanded) {
                        "Close"
                    } else {
                        "Open"
                    }
                }

            val permissionGranted = remember {
                mutableStateOf(allPermissionGranted())
            }

            val job = remember {
                mutableStateOf(Job())
            }

            var jobs by remember{
                mutableStateOf(listOf<Job>())
            }

            val scope = rememberCoroutineScope()

            scope.launch {
                mainActivityViewModel?.initializeDatabase(applicationContext)?.observe(this@MainActivity){
                    jobs = it
                }
            }

            val cameraState = rememberSaveable {
                mutableStateOf(pref?.getBoolean(CAMERA_PREFERRED_STATE, true) ?: true)
            }

            val flashLightState = remember {
                if (cameraView == null)
                    mutableStateOf(false)
                else mutableStateOf(true)
            }

            JugWriterTheme {
                BottomSheetScaffold(
                    sheetContent = SheetContent(
                        bottomSheetScaffoldState,
                        bStateTitle,
                        job, jobs
                    ),
                    topBar = AppTopAppBar(
                        title = title.toString(), permissionGranted.value,
                        cameraState, flashLightState
                    ),
                    scaffoldState = bottomSheetScaffoldState
                ) {
                    val _isLegacyMode = mainActivityViewModel?.isLegacyMode?.observeAsState()
                    if(_isLegacyMode?.value?.not()!!){

                        ContentCompact(
                                textFromCamera = when (permissionGranted.value && cameraState.value) {
                                    true -> recognizeText()
                                    else -> null
                                }, onSaveJob = saveJob(job), onClear = {
                                    job.value = Job()
                                }, job = job.value)

                        if (permissionGranted.value.not()){
                            AboutDialog(title, permissionGranted)
                        }
                    }else {

                        if (cameraState.value && permissionGranted.value) {
                            CameraView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                          )
                            ZoomSliderHorizontal()
                        } else if (permissionGranted.value.not()) {
                            AboutDialog(title, permissionGranted)
                        }

                        Content(
                            textFromCamera = when (permissionGranted.value && cameraState.value) {
                                true -> recognizeText()
                                else -> null
                            }, onSaveJob = saveJob(job), onClear = {
                                job.value = Job()
                            }, job = job.value
                        )
                    }
                }
            }
        }
    }

    private fun saveJob(
        job: MutableState<Job>,
    ): ((job: Job) -> Unit) = { _job ->

        lifecycleScope.launch {
            val id = mainActivityViewModel?.saveJob(_job)

            val str: String
            if (id != 0L) {
                job.value = Job()
                str = "Job saved successfully -  (Job ID: $id)  ${
                    SimpleDateFormat(
                        "HH:MM:SS",
                        Locale.getDefault()
                    ).format(_job.date)
                }"
            } else {
                str = "Job not saved"
            }


            val snackbar = com.google.android.material.snackbar.Snackbar.make(
                this@MainActivity.window?.peekDecorView() ?: return@launch,
                str,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            )
            val snackbarView = snackbar.view
            val snackbarLayoutParams = snackbarView.layoutParams as FrameLayout.LayoutParams
            snackbarLayoutParams.gravity = Gravity.TOP
            snackbarLayoutParams.topMargin = 70
            snackbarLayoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            snackbarView.layoutParams = snackbarLayoutParams
            snackbar.show()
        }
    }

    private fun recognizeText(): (onTextResult: (value: Float?) -> Unit) -> Unit =
        ContentCameraReturnPosition@{ callback ->

            val textRecognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            textRecognizer.process(
                InputImage.fromBitmap(
                    cameraView?.bitmap?.let {
                        var newBitmap = it
                        if (cameraView?.width != null && cameraView?.height != null) {
                            newBitmap = it.scale(
                                cameraView?.width!!,
                                cameraView?.height!!
                            )
                        }

                        newBitmap
                    } ?: return@ContentCameraReturnPosition, 0
                )
            ).addOnCompleteListener {
                if (it.isSuccessful) {
                    var text = ""
                    val result = it.result

                    val elements = mutableListOf<Text.Element>()

                    for(blocks in result.textBlocks){
                        for (lines in blocks.lines){
                            for (element in lines.elements){
                                elements.add(element)
                            }
                        }
                    }

                    elements.sortByDescending { element ->
                        return@sortByDescending  element.boundingBox?.height()
                    }

                    if (elements.size > 0) {
                        val firstElement = elements[0].text
                        if (firstElement.length > 4 && firstElement.contains('.')) {
                            text = firstElement
                        } else {
                            if (elements.size > 1) {
                                val secondElement = elements[1].text
                                if (firstElement.length == 4) {
                                    text = "$secondElement.$firstElement"
                                } else if (secondElement.length == 4) {
                                    text = "$firstElement.$secondElement"
                                }
                            }
                        }
                    }

                    if (text.isEmpty()) {
                        text = it.result.text
                        text = text.filter { ch -> (ch == '.' || ch.isDigit()) }.let {

                            var rtString = it
                            while (rtString.indexOf('.') != rtString.lastIndexOf('.')) {
                                rtString = rtString.replaceFirst(".", "")
                            }
                            rtString
                        }.trim()
                    }

                    try {
                        callback(text.toFloat())
                    } catch (ex: NumberFormatException) {
                        Toast.makeText(this@MainActivity, "No text found!", Toast.LENGTH_LONG)
                            .show()
                        callback(null)
                    }

                } else {
                    Toast.makeText(
                        this,
                        "Text Processed Error: ${it.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(null)
                }
            }

        }

    @ExperimentalMaterialApi
    @Composable
    private fun SheetContent(
        bottomSheetScaffoldState: BottomSheetScaffoldState,
        bStateTitle: String,
        job: MutableState<Job>,
        jobs: List<Job>
    ): @Composable (ColumnScope.() -> Unit) =
        {
            val scope = rememberCoroutineScope()
            val innerScaffoldState = rememberScaffoldState()
            val contextAmbient = LocalContext.current

            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        androidx.appcompat.app.AlertDialog.Builder(contextAmbient)
                            .setMessage("Do you want to delete all recent jobs?")
                            .setNegativeButton("No") { dialog, index ->
                                dialog.dismiss()
                            }.setPositiveButton("Yes") { dialog, index ->
                                dialog.dismiss()
                                scope.launch {
                                    mainActivityViewModel?.deleteJobs(jobs)
                                }
                            }.show()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                            contentDescription = ""
                        )
                    }
                },
                floatingActionButtonPosition = FabPosition.End,
                topBar = RecentTopAppBar(bottomSheetScaffoldState, scope, bStateTitle),
                scaffoldState = innerScaffoldState
            ) {

                RecentJobsView(it, jobs) {
                    scope.launch {
                        bottomSheetScaffoldState.bottomSheetState.collapse()
                    }

                    job.value = it
                }
            }
        }


    @ExperimentalMaterialApi
    @Composable
    private fun RecentTopAppBar(
        bottomSheetScaffoldState: BottomSheetScaffoldState,
        scope: CoroutineScope,
        bStateTitle: String
    ): @Composable (() -> Unit) = {
        TopAppBar(title = { Text(text = "Recent Jobs") }, actions = {
            OutlinedButton(onClick = {
                if (bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
                    scope.launch {
                        bottomSheetScaffoldState.bottomSheetState.expand()
                    }

                } else {
                    scope.launch {
                        bottomSheetScaffoldState.bottomSheetState.collapse()
                    }

                }
            }, modifier = Modifier.padding(end = 15.dp)) {
                Text(text = bStateTitle)
            }
        })
    }


    override fun onBackPressed() {
        cameraView = null
        camera = null

        this.finish()

        super.onBackPressed()
    }

    override fun onDestroy() {
        cameraExecutors.shutdownNow()
        super.onDestroy()
    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }


    @Composable
    fun AboutDialog(title: CharSequence, permissionGranted: MutableState<Boolean>) {
        val contextAmbient = LocalContext.current
        val permissionLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
                permissionGranted.value = isGranted[Manifest.permission.CAMERA] ?: false

                if (permissionGranted.value) {

                    androidx.appcompat.app.AlertDialog.Builder(contextAmbient)
                        .setView(R.layout.camera_about_notice)
                        .setNeutralButton("Ok") { dialog, index ->
                            dialog.dismiss()
                        }.show()

                }
            }

        val dialog = remember { mutableStateOf(true) }
        if (dialog.value) {
            AlertDialog(onDismissRequest = {
                dialog.value = false
            }, confirmButton = {
                Button(onClick = {
                    permissionLauncher.launch(REQUIRED_PERMISSIONS)
                    dialog.value = false
                }) {
                    Text(text = "Request Permission")
                }

            }, dismissButton = {
                Button(onClick = {
                    dialog.value = false
                }) {
                    Text(text = "Cancel")
                }
            }, text = {

                Text(
                    text = "$title will need Camera permissions to function properly",
                    textAlign = TextAlign.Center
                )
            })

        }
    }

    @Composable
    fun AppTopAppBar(
        title: String,
        cameraIsAvailable: Boolean,
        cameraState: MutableState<Boolean>,
        flashLightState: MutableState<Boolean>
    ): @Composable (() -> Unit) = {

        val openDialog = remember {
            mutableStateOf(false)
        }

        val openMoreOverflow = remember {
            mutableStateOf(false)
        }
        val _legacyModeState = mainActivityViewModel?.isLegacyMode?.observeAsState()

        TopAppBar(
            title = { Text(text = title) },
            actions = {

                if (cameraIsAvailable) {
                    if (cameraState.value) {
                        IconButton(onClick = {
                            flashLightState.value = flashLightState.value.not()
                            camera?.cameraControl?.enableTorch(flashLightState.value)
                        }) {
                            Icon(
                                painter = painterResource(
                                    id = when (flashLightState.value) {
                                        true -> R.drawable.ic_baseline_flash_off_24; else -> R.drawable.ic_baseline_flash_on_24
                                    }
                                ),
                                contentDescription = ""
                            )
                        }
                    }

                    if (_legacyModeState?.value == true) {
                        IconButton(onClick = {
                            cameraState.value = cameraState.value.not()
                            pref?.edit()?.putBoolean(CAMERA_PREFERRED_STATE, cameraState.value)
                                ?.apply()

                            if (flashLightState.value && cameraState.value.not()) {
                                flashLightState.value = flashLightState.value.not()
                                camera?.cameraControl?.enableTorch(flashLightState.value)
                            }
                        }) {
                            Icon(
                                painter = painterResource(
                                    id = when (cameraState.value) {
                                        true -> R.drawable.ic_baseline_camera_off_24; else -> R.drawable.ic_baseline_camera_on_24
                                    }
                                ),
                                contentDescription = ""
                            )
                        }
                    }

                }
                IconButton(onClick = {
                    openMoreOverflow.value = openMoreOverflow.value.not()
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_more_vert_24),
                        contentDescription = ""
                    )
                }

                DropdownMenu(expanded = openMoreOverflow.value,
                    onDismissRequest = { openMoreOverflow.value = openMoreOverflow.value.not() }) {
                    DropdownMenuItem(onClick = {
                        openMoreOverflow.value = openMoreOverflow.value.not()
                    }) {
                        Row(
                            Modifier
                                .clickable {
                                    mainActivityViewModel?.setLegacyMode(_legacyModeState?.value?.not()!!)
                                }
                                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Legacy Mode", modifier = Modifier
                                    .padding(end = 6.dp),
                                color = MaterialTheme.colors.secondaryVariant
                            )

                            Checkbox(checked = _legacyModeState?.value!!, onCheckedChange = {
                                mainActivityViewModel?.setLegacyMode(_legacyModeState.value?.not()!!)
                            })

                        }
                    }

                    DropdownMenuItem(onClick = {
                        openDialog.value = true; openMoreOverflow.value =
                        openMoreOverflow.value.not()
                    }) {
                        Text(
                            text = "About", modifier = Modifier
                                .padding(end = 6.dp),
                            color = MaterialTheme.colors.secondaryVariant
                        )
                    }
                }
            })

        if (openDialog.value) {
            AlertDialog(onDismissRequest = { openDialog.value = false },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colors.secondaryVariant,
                                    fontFamily = FontFamily.SansSerif,
                                    textDecoration = TextDecoration.Underline,
                                    fontSize = 18.sp
                                )
                            )
                            {
                                append("Made by")
                            }
                        })
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    fontFamily = FontFamily.Cursive,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 24.sp
                                )
                            ) {
                                append("~~KhemicalKoder~~")
                            }
                        })
                    }
                },
                buttons = {})
        }

    }

    @Composable
    fun Greeting(name: String) {
        Text(text = "Hello $name!")
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        JugWriterTheme {
            Greeting("Android")
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun Content() {
//    Content {
//
//    }
    }

    override fun cameraInitialize(cameraView: PreviewView?, camera: Camera?) {
        this.camera = camera
        this.cameraView = cameraView
    }

    override fun getContext(): Context {
        return this
    }

    override fun getSharedPreference(): SharedPreferences {
        return pref!!
    }

    override fun getViewModel(): MainActivityViewModel {
        return mainActivityViewModel!!
    }

    override fun getCamera(): Camera? {
        return camera
    }

    override fun getCameraView(): PreviewView? {
        return cameraView
    }
}