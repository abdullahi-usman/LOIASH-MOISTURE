package com.dahham.jobwriter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.compose.runtime.*

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
                    colors = ButtonDefaults.textButtonColors(backgroundColor = Color(0x00000021)),
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
            textFromCamera?.invoke {
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
