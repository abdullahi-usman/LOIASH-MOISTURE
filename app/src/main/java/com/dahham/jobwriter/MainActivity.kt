package com.dahham.jobwriter

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.colorspace.Rgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.component1
import androidx.core.graphics.or
import androidx.core.graphics.scale
import androidx.core.graphics.toRectF
import androidx.core.hardware.display.DisplayManagerCompat
import androidx.core.net.toUri
import androidx.core.os.EnvironmentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import com.dahham.jobwriter.ui.theme.JugWriterTheme
import com.dahham.jobwriter.ui.theme.Shapes
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {


    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val CAMERA_ZOOM_LEVEL = "CAMERA_ZOOM_LEVEL"
    private val CAMERA_PREFERRED_STATE = "CAMERA_PREFERRED_STATE"

    private var cameraView: PreviewView? = null
    private lateinit var cameraExecutors: ExecutorService
    private var camera: Camera? = null
    private var pref: SharedPreferences? = null
    private lateinit var database: WorkDatabase
    private lateinit var databaseDao: JobDao
    private var jobs: MutableState<List<Job>?> = mutableStateOf(arrayListOf())
    private var mainActivityViewModel: MainActivityViewModel? = null

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (this::database.isInitialized.not()) {
            initializeDatabase()
        }

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
                        job
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
                           }, job = job.value
                       )
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
            var id: Long
            withContext(Dispatchers.IO) {
                id = databaseDao.Insert(_job)
            }

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
        job: MutableState<Job>
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
                                    withContext(Dispatchers.IO) {
                                        databaseDao.Delete(
                                            job = jobs.value?.toTypedArray()
                                                ?: return@withContext
                                        )
                                    }
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

                RecentJobsView(it, jobs.value) {
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

    private fun initializeDatabase() {
        lifecycleScope.launch {
            val liveData: LiveData<List<Job>>
            withContext(Dispatchers.IO) {
                database = Room.databaseBuilder(
                    applicationContext,
                    WorkDatabase::class.java,
                    "WorkDatabase.db"
                ).build()
                databaseDao = database.jobDao()
                liveData = databaseDao.getAll()
            }

            liveData.observe(this@MainActivity) {
                jobs.value = it
            }

        }
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
    fun CameraView(modifier: Modifier) {

        val contextAmbient = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val cameraProvider = remember {
            ProcessCameraProvider.getInstance(contextAmbient)
        }
        val cameraZoom = rememberSaveable {
            mutableStateOf(pref?.getFloat(CAMERA_ZOOM_LEVEL, 0.5f) ?: 0.5f)
        }

        var hasInitCamera by remember{
            mutableStateOf(false)
        }

        DisposableEffect(key1 = cameraProvider) {

            cameraView = PreviewView(this@MainActivity)

            cameraView?.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

            cameraProvider.addListener({
                val cameraProviderInner = cameraProvider.get()

                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraView?.surfaceProvider)
                }

                try {
                    cameraProviderInner.unbindAll()
                    camera = cameraProviderInner.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                    if (mainActivityViewModel?.isLegacyMode?.value == true) {
                        camera?.cameraControl?.setLinearZoom(cameraZoom.value)
                    }
                } catch (ex: Exception) {
                    hasInitCamera = false
                }

                hasInitCamera = true
            }, ContextCompat.getMainExecutor(this@MainActivity))

            onDispose {
                cameraProvider.cancel(true)
                hasInitCamera = false
            }

        }

        if(hasInitCamera && cameraView != null){
            AndroidView(factory = {
                return@AndroidView cameraView!!
            }, modifier = modifier)
        }else {
            AndroidView(factory = {
                return@AndroidView LayoutInflater.from(it)
                    .inflate(R.layout.camera_not_initialized_error, null, false)
            }, modifier = modifier)
        }
    }

    @Composable
    private fun ZoomSliderHorizontal() {
        val cameraZoom = rememberSaveable {
            mutableStateOf(pref?.getFloat(CAMERA_ZOOM_LEVEL, 0.5f) ?: 0.5f)
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(text = "Zoom: ", fontSize = 14.sp)

                Spacer(modifier = Modifier.width(6.dp))

                Slider(value = cameraZoom.value, onValueChange = {
                    cameraZoom.value = it
                    camera?.cameraControl?.setLinearZoom(it)?.addListener({
                        pref?.edit()?.putFloat(CAMERA_ZOOM_LEVEL, it)?.apply()
                    }, cameraExecutors)
                })
            }

        }
    }

    @Composable
    fun ContentCompact(
        textFromCamera: ((onTextResult: (value: Float?) -> Unit) -> Unit)?,
        onSaveJob: ((job: Job) -> Unit)? = null, onClear: (() -> Unit), job: Job
    ) {

        val saveableJob by remember(key1 = job) {
            mutableStateOf(job.copy())
        }

        val w1 = rememberSaveable(inputs = arrayOf(job)) {
            mutableStateOf(job.w1.toString())
        }
        val w2 = rememberSaveable(inputs = arrayOf(job)) {
            mutableStateOf(job.w2.toString())
        }
        val w3 = rememberSaveable(inputs = arrayOf(job)) {
            mutableStateOf(job.w3.toString())
        }

        val modified = remember {
            mutableStateOf(job != saveableJob)
        }

        val answer = rememberSaveable(inputs = arrayOf(w1.value, w2.value, w3.value, saveableJob.lastJobOperator)) {

            saveableJob.w1 = w1.value.toFloat()
            saveableJob.w2 = w2.value.toFloat()
            saveableJob.w3 = w3.value.toFloat()

            modified.value = saveableJob.sameAs(job).not()

            return@rememberSaveable try {
                mutableStateOf(saveableJob.calculate())
            } catch (ex: ArithmeticException) {
                mutableStateOf(0f)
            }
        }

        ConstraintLayout {
            val (cameraView, answerView, slider, contentView, snapBtn, saveBtn, clearBtn, jobOperatorBtn) = createRefs()

            CameraView(modifier = Modifier
                .fillMaxSize()
                .constrainAs(cameraView) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
            )

            Text(text = buildAnnotatedString {
                append("Answer: ${answer.value.toString()}  (${saveableJob.lastJobOperator.name})\n")
                append("Current Job: " + if (job.uid == 0) "New (Unsaved)" else "ID - " + job.uid.toString() + if (modified.value) " (Modified)" else "")
            } , modifier = Modifier
                .constrainAs(answerView) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                }
                .fillMaxWidth()
                .background(Color.Black), textAlign = TextAlign.Center, color = Color.White)


//                            Slider(value = 0.9f, onValueChange =  {
//
//                            }, modifier = Modifier.constrainAs(slider){
//                                top.linkTo(parent.top)
//                                bottom.linkTo(parent.bottom)
//                            }.graphicsLayer {
//                                rotationZ = -90f
//                                translationX = -windowManager.currentWindowMetrics.bounds.toRectF().width() / 2
//                            })

            var selectedPosition by rememberSaveable {
                if (w1.value.toFloat() == 0f) {
                    mutableStateOf(1)
                }else if (w1.value.toFloat() == 0f){
                    mutableStateOf(2)
                }else {
                    mutableStateOf(3)
                }
            }

            val jobAnalysisToggle = remember {
                mutableStateOf(false)
            }

            Row(modifier = Modifier.constrainAs(contentView) {
                top.linkTo(answerView.bottom, 8.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }, horizontalArrangement = Arrangement.End) {
                (1..3).forEach {
                    TextButton(
                        border = BorderStroke(1.dp, if (selectedPosition == it)  MaterialTheme.colors.primary else MaterialTheme.colors.secondaryVariant),
                        colors = androidx.compose.material.ButtonDefaults.textButtonColors(backgroundColor = Color(0x00000021)),
                        modifier = Modifier
                            .padding(10.dp),
                        onClick = {
                            selectedPosition = it
                        }) {
                        Text(softWrap = false,
                            maxLines = 2,
                            text = buildAnnotatedString {
                            pushStyle(style = SpanStyle(color = if (selectedPosition == it)  MaterialTheme.colors.primary else Color.White))
                            pushStyle(style = ParagraphStyle(textAlign = TextAlign.Center))
                            this.append("W$it\n")
                            this.append(
                                when(it){
                                    1 -> w1;
                                    2 -> w2;
                                    else -> w3
                                }.value
                            )
                        })
                    }
                }
            }

            IconButton(onClick = {
                recognizeText().invoke {
                    when(selectedPosition){
                        1 -> w1.value = (it ?: 0.0f).toString();
                        2 -> w2.value = (it ?: 0.0f).toString();
                        else -> w3.value = (it ?: 0.0f).toString();
                    }

                    if (selectedPosition != 3)selectedPosition = selectedPosition.inc()
                }
            }, modifier = Modifier.constrainAs(snapBtn){
                bottom.linkTo(parent.bottom, 75.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }) {
                Icon(painter = painterResource(id = R.drawable.ic_baseline_camera_24), contentDescription = "",
                    tint = MaterialTheme.colors.secondaryVariant)
            }

            Button(onClick = {
                onSaveJob?.invoke(saveableJob)
            }, modifier = Modifier.constrainAs(saveBtn){
                top.linkTo(snapBtn.top)
                bottom.linkTo(snapBtn.bottom)
                start.linkTo(snapBtn.end, 16.dp)
            }) {
                Text(text = "Save")
            }

            Button(onClick = {
                onClear.invoke()
            }, modifier = Modifier.constrainAs(clearBtn){
                top.linkTo(snapBtn.top)
                bottom.linkTo(snapBtn.bottom)
                end.linkTo(snapBtn.start, 16.dp)
            }) {
                Text(text = "Clear")
            }

            IconButton(onClick = {
                jobAnalysisToggle.value = jobAnalysisToggle.value.not()
            },  modifier = Modifier.constrainAs(jobOperatorBtn){
                top.linkTo(clearBtn.top)
                bottom.linkTo(clearBtn.bottom)
                start.linkTo(parent.start, 8.dp)
            }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_more_vert_24),
                        contentDescription = "", tint = MaterialTheme.colors.primary)
            }

            DropdownMenu(
                expanded = jobAnalysisToggle.value,
                onDismissRequest = {
                    jobAnalysisToggle.value = jobAnalysisToggle.value.not()
                }) {
                Job.JobOperator.values().forEach {
                    DropdownMenuItem(onClick = {
                        jobAnalysisToggle.value = jobAnalysisToggle.value.not()
                        saveableJob.lastJobOperator = it
                    }) {
                        Text(text = it.name)
                    }
                }

            }

        }

    }

    @Composable
    fun Content(
        textFromCamera: ((onTextResult: (value: Float?) -> Unit) -> Unit)?,
        onSaveJob: ((job: Job) -> Unit)? = null, onClear: (() -> Unit), job: Job
    ) {

        /*
    * TODO: This is ugly, so so ugly, make sure you refactor!!!
    *  find better way and get rid of w1, w2, w3 and saveableJob
    * TODO: And also this compose function is so big and confusing
    *
    * */
        val saveableJob by remember(key1 = job) {
            mutableStateOf(job.copy())
        }

        val w1 = rememberSaveable(inputs = arrayOf(job)) {
            mutableStateOf(job.w1.toString())
        }
        val w2 = rememberSaveable(inputs = arrayOf(job)) {
            mutableStateOf(job.w2.toString())
        }
        val w3 = rememberSaveable(inputs = arrayOf(job)) {
            mutableStateOf(job.w3.toString())
        }

        val modified = remember {
            mutableStateOf(job != saveableJob)
        }

        val scope = rememberCoroutineScope()

        val answer = rememberSaveable(inputs = arrayOf(w1.value, w2.value, w3.value, saveableJob.lastJobOperator)) {

            saveableJob.w1 = w1.value.toFloat()
            saveableJob.w2 = w2.value.toFloat()
            saveableJob.w3 = w3.value.toFloat()

            modified.value = saveableJob.sameAs(job).not()

            return@rememberSaveable try {
                mutableStateOf(saveableJob.calculate())
            } catch (ex: ArithmeticException) {
                mutableStateOf(0f)
            }
        }

        val jobAnalysisToggle = remember {
            mutableStateOf(false)
        }

        val scrollState = rememberScrollState()
        val contextAmbient = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = when (textFromCamera) {
                        null -> 0.dp; else -> 20.dp
                    },
                    bottom = 300.dp
                ), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = when (textFromCamera) {
                null -> Arrangement.Center; else -> Arrangement.Top
            }
        ) {

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Current Job: " + if (job.uid == 0) "New Job (Unsaved)" else "ID - " + job.uid.toString() + if (modified.value) " (Modified)" else "",
                textAlign = TextAlign.Start
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween
            ) {

                OutlinedButton(onClick = {
                    jobAnalysisToggle.value = jobAnalysisToggle.value.not()
                }) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = saveableJob.lastJobOperator.name)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_expand_more_24),
                            contentDescription = ""
                        )
                    }
                }

                DropdownMenu(
                    expanded = jobAnalysisToggle.value,
                    onDismissRequest = {
                        jobAnalysisToggle.value = jobAnalysisToggle.value.not()
                    }) {
                    Job.JobOperator.values().forEach {
                        DropdownMenuItem(onClick = {
                            jobAnalysisToggle.value = jobAnalysisToggle.value.not()
                            saveableJob.lastJobOperator = it
                        }) {
                            Text(text = it.name)
                        }
                    }

                }

                Row {

                    Button(onClick = {
                        if (saveableJob.uid != 0 && modified.value) {
                            androidx.appcompat.app.AlertDialog.Builder(contextAmbient)
                                .setMessage("This job is from the database but currently modified. \n Do you want clear changes?")
                                .setPositiveButton("Yes") { dialog, index ->
                                    dialog.dismiss()
                                    onClear()
                                }.setNegativeButton("No") { dialog, index ->
                                    dialog.dismiss()
                                }.show()
                        } else {
                            onClear()
                        }
                    }) {
                        Text(text = "Clear")
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    Button(onClick = {
                        onSaveJob?.invoke(saveableJob)
                    }) {
                        Text(text = "Save Job")
                    }
                }
            }

            Text(text = "Answer", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .border(.5.dp, color = Color.DarkGray, shape = RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = answer.value.toString(),
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {

                arrayOf(w1, w2, w3).forEachIndexed { index, inputField ->
                    key(index) {

                        val anim = remember{
                            Animatable(1f)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            OutlinedTextField(
                                modifier = Modifier.alpha(anim.value),
                                singleLine = true,
                                value = inputField.value,
                                onValueChange = { __ignoreStr ->
                                    scope.launch {
                                        var tempStr = __ignoreStr
                                        withContext(Dispatchers.Default) {

                                            while (tempStr.indexOf('.') != tempStr.lastIndexOf('.')) {
                                                tempStr = tempStr.replace(".", "").trim().plus(".")
                                            }

                                            while (tempStr.startsWith('0') && tempStr.indexOf('.') != 1) {
                                                tempStr = tempStr.replaceFirst("0", "")
                                            }

                                            if (tempStr.isEmpty()) {
                                                tempStr = "0"
                                            }

                                            if (inputField.value.length == 1 && tempStr.length == 2 && inputField.value == "0") {
                                                tempStr = tempStr.replaceFirst("0", "")
                                            }

                                            tempStr = tempStr.filter { (it == '.' || it.isDigit()) }
                                        }
                                        inputField.value = tempStr
                                    }
                                },
                                label = { Text(text = when(index + 1){ 1 -> "First"; 2 -> "Second"; else -> "Third" } +" Weight(W${index+1})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            if (textFromCamera != null) {
                                IconButton(onClick = {
                                    if(anim.isRunning){
                                        scope.launch { anim.stop(); anim.animateTo(1f);  }
                                        return@IconButton
                                    }else {

                                        textFromCamera.invoke{
                                            if (it != null) {
                                                inputField.value = it.toString()
                                            }
                                            scope.launch { if (anim.isRunning) anim.stop(); anim.animateTo(1f);  }
                                        }
                                        scope.launch { anim.animateTo(0.3f, animationSpec = infiniteRepeatable(
                                        animation = tween(400))
                                        )  }

                                    }
                                }, modifier = Modifier.padding(6.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_baseline_camera_24),
                                        contentDescription = ""
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                }
            }
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
                        Row(Modifier.clickable {
                            mainActivityViewModel?.setLegacyMode(_legacyModeState?.value?.not()!!)
                        }.padding(vertical = 4.dp)) {
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
}