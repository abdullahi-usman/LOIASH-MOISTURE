package com.dahham.jobwriter

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.scale
import androidx.lifecycle.*
import androidx.room.Room
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivityViewModel(val sharedPreferences: SharedPreferences): ViewModel() {

    private var _isLegacyMode = MutableLiveData(false)
    private lateinit var database: WorkDatabase
    private lateinit var databaseDao: JobDao

    var isLegacyMode: LiveData<Boolean> = _isLegacyMode

    init {
        _isLegacyMode.value = sharedPreferences.getBoolean("LEGACY_MODE", false)
    }

    fun setLegacyMode(modeLegacy: Boolean){
        sharedPreferences.edit().putBoolean("LEGACY_MODE", modeLegacy).apply()
        _isLegacyMode.value = modeLegacy
    }

    suspend fun initializeDatabase(applicationContext: Context):LiveData<List<Job>> {

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

        return liveData
    }

    suspend fun saveJob(job: Job): Long{
        var id: Long
        withContext(Dispatchers.IO) {
            id = databaseDao.Insert(job)
        }


        return id
    }

    suspend fun deleteJobs(jobs: List<Job>){
        withContext(Dispatchers.IO) {
            databaseDao.Delete(
                *jobs.toTypedArray())
        }
    }
}