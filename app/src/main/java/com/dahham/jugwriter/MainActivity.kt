package com.dahham.jugwriter

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.room.*
import androidx.room.OnConflictStrategy.REPLACE
import com.dahham.jugwriter.ui.theme.JugWriterTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.actor
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

    private lateinit var cameraExecutor: ExecutorService
    private var cameraView: PreviewView? = null
    private lateinit var database: WorkDatabase
    private lateinit var databaseDao: JobDao
    private var jobs: MutableState<List<Job>?> = mutableStateOf(arrayListOf())

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        initializeDatabase()

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

            var job by remember {
                mutableStateOf(Job())
            }


            JugWriterTheme {
                BottomSheetScaffold(
                    sheetContent = {
                        Scaffold(
                            floatingActionButton = {
                                FloatingActionButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            databaseDao.Delete(
                                                job = jobs.value?.toTypedArray() ?: return@withContext
                                            )
                                        }
                                    }
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
                            }, scaffoldState = innerScaffoldState) {

                            RecentJobsView(it, jobs.value){
                                scope.launch {
                                    bottomSheetScaffoldState.bottomSheetState.collapse()
                                }

                                job = it
                            }
                        }
                    },
                    topBar = {
                        AppTopAppBar(title = title.toString())
                    }, scaffoldState = bottomSheetScaffoldState
                ) {

                    if (permissionGranted.value) {
                        CameraView(
                            contextAmbient, lifecycleOwner,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) { _cameraView ->
                            cameraView = _cameraView
                        }
                    } else {
                        AboutDialog(title, permissionGranted)
                    }



                    Content(textFromCamera = when (permissionGranted.value) {
                        true -> Content@{ callback ->

                            val textRecognizer =
                                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                            textRecognizer.process(
                                InputImage.fromBitmap(
                                    cameraView?.bitmap ?: return@Content, 0
                                )
                            ).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    var text = it.result.text
                                    Toast.makeText(
                                        this,
                                        "text processed: $text",
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                    text = text.filter { ch -> ch == '.' || ch.isDigit() }.trim()
                                    callback(text.toBigDecimalOrNull())
                                } else {
                                    callback(null)
                                }
                            }

                        }
                        else -> null
                    }, onSaveJob = { _job ->
                        job = Job()
                        scope.launch {
                            var id: Long
                            withContext(Dispatchers.IO) {
                                id = databaseDao.Insert(_job)
                            }
                            if (id != 0L) {
                                job = Job()
                                bottomSheetScaffoldState.snackbarHostState.showSnackbar("Job saved successfully")
                            }else {
                                bottomSheetScaffoldState.snackbarHostState.showSnackbar("Job not saved")
                            }
                        }
                    }, job = job)
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
        cameraExecutor.shutdown()
    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

}

@Composable
fun AboutDialog(title: CharSequence, permissionGranted: MutableState<Boolean>) {
    val permissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
            permissionGranted.value = isGranted[Manifest.permission.CAMERA] ?: false
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
    modifier: Modifier,
    onCameraSetup: (cameraView: PreviewView) -> Unit
) {

    val cameraProvider = remember {
        ProcessCameraProvider.getInstance(contextAmbient)
    }
    AndroidView(factory = {
        val cameraView = PreviewView(it)

        cameraProvider.addListener({
            val cameraProviderInner = cameraProvider.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(cameraView.surfaceProvider)
            }

            try {
                cameraProviderInner.unbindAll()
                cameraProviderInner.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (ex: Exception) {

            }

            onCameraSetup(cameraView)
        }, ContextCompat.getMainExecutor(it))



        return@AndroidView cameraView
    }, modifier = modifier)
}

operator fun BigDecimal?.plus(value: BigDecimal?): BigDecimal {
    if (this != null && value == null)
        return this

    if (this == null && value != null)
        return value

    return this!!.plus(other = value!!)
}


@Composable
fun Content(
    textFromCamera: ((onTextResult: (value: BigDecimal?) -> Unit) -> Unit)?,
    onSaveJob: ((job: Job) -> Unit)? = null, job: Job
) {


    val w1 = rememberSaveable(inputs = arrayOf(job.w1)) {
        mutableStateOf(job.w1.toEngineeringString())
    }
    var w2 = rememberSaveable(inputs = arrayOf(job.w2)) {
        mutableStateOf(job.w2.toEngineeringString())
    }
    var w3 = rememberSaveable(inputs = arrayOf(job.w3)) {
        mutableStateOf(job.w3.toEngineeringString())
    }

    val answer = rememberSaveable(inputs = arrayOf(w1, w2, w3)) {
        job.w1 = w1.value.toBigDecimal()
        job.w2 = w2.value.toBigDecimal()
        job.w3 = w3.value.toBigDecimal()

        mutableStateOf(job.calculate())
    }

    var currentCameraPosition = w1
    val onCameraText: (text: BigDecimal?) -> Unit = {
        if (it != null) {
            currentCameraPosition.value = it.toEngineeringString()
        }
    }

    val jobAnalysisToggle = remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = when (textFromCamera) {
                    null -> 0.dp; else -> 20.dp
                }
            ), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = when(textFromCamera){ null -> Arrangement.Center; else -> Arrangement.Top }
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween
        ) {

            OutlinedButton(onClick = { jobAnalysisToggle.value = jobAnalysisToggle.value.not() }) {
                Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = job.lastJobOperator.name)
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
                        job.lastJobOperator = it
                    }) {
                        Text(text = it.name)
                    }
                }

            }

            Button(onClick = {
                onSaveJob?.invoke(job)
            }) {
                Text(text = "Save Job")
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = w1.value,
                onValueChange = { it ->

                    if (it.isEmpty()){
                        w1.value = "0"
                    }else if (w1.value == "0" && it.length == 2 && it[1] == '0'){
                        w1.value = it[0].toString()
                    }else if(it.endsWith('.')){
                        w1.value = it.replace(".", "").plus('.')
                    }else {
                        w1.value = it.trim { (it != '.' && it.isDigit().not()) || it.isDigit().not() }.toBigDecimal().toEngineeringString()
                    }
                },
                label = { Text(text = "First Weight(W1)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

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
                value = w2.value,
                onValueChange = {
                    if (it.isEmpty()) {
                        w2.value = "0"
                    }else if (w2.value == "0" && it.length == 2 && it[1] == '0'){
                        w2.value = it[0].toString()
                    } else if(it.endsWith('.')){
                        w2.value = it.replace(".", "").plus('.')
                    }else {
                        w2.value =  it.trim { (it != '.' && it.isDigit().not()) || it.isDigit().not() }.toBigDecimal().toEngineeringString()
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
                value = w3.value,
                onValueChange = {
                    if (it.isEmpty()){
                        w3.value = "0"
                    }else if (w3.value == "0" && it.length == 2 && it[1] == '0'){
                        w3.value = it[0].toString()
                    } else if(it.endsWith('.')){
                        w3.value = it.replace(".", "").plus('.')
                    }else {
                        w3.value = it.trim { (it != '.' && it.isDigit().not()) || it.isDigit().not() }.toBigDecimal().toEngineeringString()
                    }
                },
                label = { Text(text = "Third Weight(W3)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

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


@Composable
fun AppTopAppBar(title: String) {

    val openDialog = remember {
        mutableStateOf(false)
    }

    TopAppBar(title = { Text(text = title) }, actions = {
        TextButton(onClick = { openDialog.value = true }) {
            Text(
                text = "About", modifier = Modifier
                    .padding(end = 6.dp),
                color = MaterialTheme.colors.secondaryVariant
            )
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
fun RecentJobsView( paddingValues: PaddingValues, all: List<Job>?, onContinueJobClicked: (job: Job) -> Unit) {


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

            itemsIndexed(items = all, key =  {index, item -> return@itemsIndexed item.w1.toString() + item.w2.toString() + item.w3.toString() }) { index, job ->
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
            Text(text = "ID: ${job.uid} -  (${job.lastJobOperator.name})", color = MaterialTheme.colors.secondary)
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
                    text = "Result: ${job.w3}",
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
    var w1: BigDecimal = BigDecimal(0),
    var w2: BigDecimal = BigDecimal(0),
    var w3: BigDecimal = BigDecimal(0),
    var lastJobOperator: JobOperator = JobOperator.LOI
) {


    enum class JobOperator {

        LOI {
            override fun calculate(): BigDecimal {
                return BigDecimal(0)
            }
        },
        ASH {
            override fun calculate(): BigDecimal {
                return BigDecimal(0)
            }
        },
        MOISTURE {
            override fun calculate(): BigDecimal {
                return BigDecimal(0)
            }
        };

        abstract fun calculate(): BigDecimal
    }

    fun calculate(): BigDecimal {
        return lastJobOperator.calculate()
    }

}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY date DESC")
    fun getAll(): LiveData<List<Job>>

    @Query("SELECT * FROM jobs WHERE uid == :id")
    fun getJobById(id: Long): Job

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun Insert(job: Job): Long

    @Delete
    fun Delete(vararg job: Job)
}

@Database(entities = [Job::class], version = 1)
@TypeConverters(Converters::class)
abstract class WorkDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
}