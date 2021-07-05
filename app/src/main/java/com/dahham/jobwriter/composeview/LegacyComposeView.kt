package com.dahham.jobwriter

import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor


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
                            AlertDialog.Builder(contextAmbient)
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
                                            animation = tween(400)
                                        )
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
fun ZoomSliderHorizontal() {
    val cameraZoom = rememberSaveable {
        mutableStateOf(mainActivityUtils.getSharedPreference().getFloat(CAMERA_ZOOM_LEVEL, 0.5f) ?: 0.5f)
    }

    Spacer(modifier = Modifier.height(2.dp))

    Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(text = "Zoom: ", fontSize = 14.sp)

            Spacer(modifier = Modifier.width(6.dp))

            Slider(value = cameraZoom.value, onValueChange = {
                cameraZoom.value = it
                mainActivityUtils.getCamera()?.cameraControl?.setLinearZoom(it)?.addListener({
                    mainActivityUtils.getSharedPreference().edit()?.putFloat(CAMERA_ZOOM_LEVEL, it)?.apply()
                }, mainActivityUtils.getContext() as Executor)
            })
        }

    }
}