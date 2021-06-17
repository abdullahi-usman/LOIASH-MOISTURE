package com.dahham.jugwriter

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.lifecycle.*
import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE
import com.dahham.jugwriter.ui.theme.JugWriterTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    private val CAMERA_ZOOM_LEVEL = "CAMERA_ZOOM_LEVEL"
    private var cameraView: PreviewView? = null
    private lateinit var cameraExecutors: ExecutorService
    private var camera: Camera? = null
    private var pref: SharedPreferences? = null
    private lateinit var database: WorkDatabase
    private lateinit var databaseDao: JobDao
    private var jobs: MutableState<List<Job>?> = mutableStateOf(arrayListOf())

    private val CAMERA_PREFERRED_STATE = "CAMERA_PREFERRED_STATE"

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeDatabase()

        if (pref == null){
            pref = getSharedPreferences("CAMERA_PREF", 0)
        }

        if (this::cameraExecutors.isInitialized.not()){
            cameraExecutors = Executors.newSingleThreadExecutor()
        }

        setContent {

            val contextAmbient = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()
            val innerScaffoldState = rememberScaffoldState()
            val scope = rememberCoroutineScope()

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

            val cameraZoomState = rememberSaveable {
                mutableStateOf(pref?.getFloat(CAMERA_ZOOM_LEVEL, 0.5f) ?: 0.5f)
            }

            LaunchedEffect(key1 = flashLightState.value) {
                camera?.cameraControl?.enableTorch(flashLightState.value)
            }

            LaunchedEffect(key1 = cameraZoomState.value) {
                camera?.cameraControl?.setLinearZoom(cameraZoomState.value)?.addListener({
                    pref?.edit()?.putFloat(CAMERA_ZOOM_LEVEL, cameraZoomState.value)?.commit()
                }, cameraExecutors)

            }

            LaunchedEffect(key1 = cameraState.value){
                pref?.edit()?.putBoolean(CAMERA_PREFERRED_STATE, cameraState.value)
                    ?.apply()
            }

            JugWriterTheme {
                BottomSheetScaffold(
                    sheetContent = {
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
                            topBar = {
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
                            }, scaffoldState = innerScaffoldState
                        ) {

                            RecentJobsView(it, jobs.value) {
                                scope.launch {
                                    bottomSheetScaffoldState.bottomSheetState.collapse()
                                }

                                job.value = it
                            }
                        }
                    },
                    topBar = {
                        AppTopAppBar(
                            title = title.toString(), permissionGranted.value,
                            cameraState, flashLightState
                        )
                    }, scaffoldState = bottomSheetScaffoldState
                ) {

                    if (cameraState.value && permissionGranted.value) {
                        CameraView(
                            contextAmbient, lifecycleOwner, cameraZoomState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) { _cameraView, _camera ->
                            cameraView = _cameraView
                            camera = _camera
                            camera?.cameraControl?.setLinearZoom(cameraZoomState.value)
                        }
                    } else if (permissionGranted.value.not()) {
                        AboutDialog(title, permissionGranted)
                    }

                    Content(textFromCamera = when (permissionGranted.value) {
                        true -> Content@{ callback ->

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
                                    } ?: return@Content, 0
                                )
                            ).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    var text = it.result.text
                                    text = text.filter { ch -> (ch == '.' || ch.isDigit()) }.let {
                                        var rtString = it
                                        while (rtString.indexOf('.') != rtString.lastIndexOf(
                                                '.'
                                            )
                                        ) {
                                            rtString = rtString.replaceFirst(".", "")
                                        }
                                        rtString
                                    }.trim()
                                    callback(text.toFloat())
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
                        else -> null
                    }, onSaveJob = { _job ->
                        scope.launch {
                            var id: Long
                            withContext(Dispatchers.IO) {
                                id = databaseDao.Insert(_job)
                            }
                            if (id != 0L) {
                                job.value = Job()
                                bottomSheetScaffoldState.snackbarHostState.showSnackbar("Job saved successfully")
                            } else {
                                bottomSheetScaffoldState.snackbarHostState.showSnackbar("Job not saved")
                            }
                        }
                    }, onClear = {
                        job.value = Job()
                    }, job = job.value
                    )
                }
            }
        }
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutors.shutdown()
    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

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
                permissionLauncher.launch(MainActivity.REQUIRED_PERMISSIONS)
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
fun CameraView(
    contextAmbient: Context,
    lifecycleOwner: LifecycleOwner,
    cameraFocus: MutableState<Float>,
    modifier: Modifier,
    onCameraSetup: (cameraView: PreviewView, camera: Camera) -> Unit
) {

    val cameraProvider = remember {
        ProcessCameraProvider.getInstance(contextAmbient)
    }
    AndroidView(factory = {
        val cameraView = PreviewView(it)
        var camera: Camera? = null
        cameraView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        cameraProvider.addListener({
            val cameraProviderInner = cameraProvider.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(cameraView.surfaceProvider)
            }

            try {
                cameraProviderInner.unbindAll()
                camera = cameraProviderInner.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )

                camera?.cameraControl?.setLinearZoom(0.8f)
            } catch (ex: Exception) {

            }

            onCameraSetup(cameraView, camera!!)
        }, ContextCompat.getMainExecutor(it))



        return@AndroidView cameraView
    }, modifier = modifier)

    Spacer(modifier = Modifier.height(2.dp))

    Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(text = "Zoom: ", fontSize = 14.sp)

            Spacer(modifier = Modifier.width(6.dp))

            Slider(value = cameraFocus.value, onValueChange = {
                cameraFocus.value = it
            })
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

    val answer = rememberSaveable(
        inputs = arrayOf(
            w1.value,
            w2.value,
            w3.value,
            saveableJob.lastJobOperator
        )
    ) {
        saveableJob.w1 = w1.value.toFloat()
        saveableJob.w2 = w2.value.toFloat()
        saveableJob.w3 = w3.value.toFloat()

        modified.value = saveableJob.sameAs(job).not()

        return@rememberSaveable try {
            mutableStateOf(saveableJob.calculate())
        } catch (ex: ArithmeticException) {
            mutableStateOf(BigDecimal(0))
        }
    }

    var currentCameraPosition = w1
    val onCameraText: (text: Float?) -> Unit = {
        if (it != null) {
            currentCameraPosition.value = it.toString()
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
            )
        , horizontalAlignment = Alignment.CenterHorizontally,
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

            OutlinedButton(onClick = { jobAnalysisToggle.value = jobAnalysisToggle.value.not() }) {
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
                onDismissRequest = { jobAnalysisToggle.value = jobAnalysisToggle.value.not() }) {
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

        Column(modifier = Modifier
                .fillMaxSize()) {
            val localDensity = LocalDensity.current

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                OutlinedTextField(
                    singleLine = true,
                    value = w1.value,
                    onValueChange = { __ignoreStr ->
                        scope.launch {
                            var tempStr = __ignoreStr
                            withContext(Dispatchers.Default) {

                                while (tempStr.indexOf('.') != tempStr.lastIndexOf('.')) {
                                    tempStr = tempStr.replace(".", "").trim().plus(".")
                                }

                                while (tempStr.startsWith('0') && tempStr.indexOf('.') != 1){
                                    tempStr = tempStr.replaceFirst("0", "")
                                }

                                if (tempStr.isEmpty()) {
                                    tempStr = "0"
                                }

                                if (w1.value.length == 1 && tempStr.length == 2 && w1.value == "0"){
                                    tempStr = tempStr.replaceFirst("0", "")
                                }

                                tempStr = tempStr.filter { (it == '.' || it.isDigit()) }
                            }
                            w1.value = tempStr
                        }
                    },
                    label = { Text(text = "First Weight(W1)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (textFromCamera != null) {
                    IconButton(onClick = {
                        currentCameraPosition = w1
                        textFromCamera.invoke(onCameraText)
                    }, modifier = Modifier.padding(6.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_camera_24),
                            contentDescription = ""
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                OutlinedTextField(
                   /* modifier = Modifier
                        .onFocusChanged { event ->
                        if (event.toString() == "Active" && scrollState.isScrollInProgress.not()) {
                            scope.launch {
                                with(localDensity) {
                                    scrollState.animateScrollTo(70.dp.toPx().toInt())
                                }

                            }
                        }
                    },*/
                    singleLine = true,
                    value = w2.value,
                    onValueChange = {  __ignoreStr ->
                        scope.launch {
                            var tempStr = __ignoreStr
                            withContext(Dispatchers.Default) {

                                while (tempStr.indexOf('.') != tempStr.lastIndexOf('.')) {
                                    tempStr = tempStr.replace(".", "").trim().plus(".")
                                }

                                while (tempStr.startsWith('0') && tempStr.indexOf('.') != 1){
                                    tempStr = tempStr.replaceFirst("0", "")
                                }

                                if (tempStr.isEmpty()) {
                                    tempStr = "0"
                                }

                                if (w2.value.length == 1 && tempStr.length == 2 && w2.value == "0"){
                                    tempStr = tempStr.replaceFirst("0", "")
                                }

                                tempStr = tempStr.filter { (it == '.' || it.isDigit()) }
                            }
                            w2.value = tempStr
                        }
                    },
                    label = { Text(text = "Second Weight(W2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (textFromCamera != null) {
                    IconButton(onClick = {
                        currentCameraPosition = w2
                        textFromCamera.invoke(onCameraText)
                    }, modifier = Modifier.padding(6.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_camera_24),
                            contentDescription = ""
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {


                OutlinedTextField(
                   /* modifier = Modifier.onFocusChanged { event ->
                        if (event.toString() == "Active" && scrollState.isScrollInProgress.not()) {
                            scope.launch {
                                with(localDensity) {
                                    scrollState.animateScrollTo(130.dp.toPx().toInt())
                                }

                            }
                        }
                    },*/
                    singleLine = true,
                    value = w3.value,
                    onValueChange = { __ignoreStr ->
                        scope.launch {
                            var tempStr = __ignoreStr
                            withContext(Dispatchers.Default) {

                                while (tempStr.indexOf('.') != tempStr.lastIndexOf('.')) {
                                    tempStr = tempStr.replace(".", "").trim().plus(".")
                                }

                                while (tempStr.startsWith('0') && tempStr.indexOf('.') != 1){
                                    tempStr = tempStr.replaceFirst("0", "")
                                }

                                if (tempStr.isEmpty()) {
                                    tempStr = "0"
                                }

                                if (w3.value.length == 1 && tempStr.length == 2 && w3.value == "0"){
                                    tempStr = tempStr.replaceFirst("0", "")
                                }

                                tempStr = tempStr.filter { (it == '.' || it.isDigit()) }
                            }
                            w3.value = tempStr
                        }
                    },
                    label = { Text(text = "Third Weight(W3)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (textFromCamera != null) {

                    IconButton(onClick = {
                        currentCameraPosition = w3
                        textFromCamera.invoke(onCameraText)
                    }, modifier = Modifier.padding(6.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_camera_24),
                            contentDescription = ""
                        )
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
) {

    val openDialog = remember {
        mutableStateOf(false)
    }

    val openMoreOverflow = remember {
        mutableStateOf(false)
    }

    TopAppBar(
        title = { Text(text = title) },
        actions = {

            if (cameraIsAvailable) {
                if(cameraState.value) {
                    IconButton(onClick = { flashLightState.value = flashLightState.value.not() }) {
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

                IconButton(onClick = { cameraState.value = cameraState.value.not() }) {
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
                    openDialog.value = true; openMoreOverflow.value = openMoreOverflow.value.not()
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
                .padding(horizontal = 12.dp, vertical = 20.dp), contentAlignment = Alignment.Center
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
            DateFormat.SHORT,
            Locale.getDefault()
        ).format(job.date).toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "ID: ${job.uid} -  (${job.lastJobOperator.name})",
                color = MaterialTheme.colors.secondary
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

@Entity(tableName = "Jobs")
data class Job(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    var date: Date = Calendar.getInstance().time,
    var w1: Float = 0f,
    var w2: Float = 0f,
    var w3: Float = 0f,
    var lastJobOperator: JobOperator = JobOperator.LOI
) {

    var title: String = ""
    var notes: String = ""

    /*Ugly hack to get around database migration becuase of adding new fields*/
    var ext1: String = ""
    var ext2: String = ""
    var ext3: String = ""

    enum class JobOperator {

        LOI {
            override fun calculate(job: Job): Float {
                return ((job.w2 - job.w3) / (job.w2 - job.w1)) * 100f
            }
        },
        ASH {
            override fun calculate(job: Job): Float {
                return 100 - LOI.calculate(job)
            }
        },
        MOISTURE {
            override fun calculate(job: Job): Float {
                return LOI.calculate(job)
            }
        };

        abstract fun calculate(job: Job): Float
    }

    fun calculate(): Float {
        return lastJobOperator.calculate(job = this)
    }

    //Compose may be using equals in some way, so we cant override,
    //Currently we dont know how to override equals in a safe way wihtout breaking compose [job.value]
    fun sameAs(other: Any?): Boolean {
        if (other != null && other is Job && other.uid == uid
            && other.w1 == w1 && other.w2 == w2 && other.w3 == w3
            && other.lastJobOperator == lastJobOperator
        ) {
            return true
        }

        return false
    }

}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY date DESC")
    fun getAll(): LiveData<List<Job>>

    @Query("SELECT * FROM jobs WHERE uid == :id")
    fun getJobById(id: Long): Job

    @Insert(onConflict = REPLACE)
    fun Insert(job: Job): Long

    @Delete
    fun Delete(vararg job: Job)
}

@Database(entities = [Job::class], version = 1)
@TypeConverters(Converters::class)
abstract class WorkDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
}