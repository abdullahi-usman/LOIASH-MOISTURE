package com.dahham.jobwriter

import android.content.Context
import androidx.lifecycle.*

class MainActivityViewModelFactory constructor(val context: MainActivity): AbstractSavedStateViewModelFactory(context, null) {


    override fun <T : ViewModel?> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return MainActivityViewModel(context.getSharedPreferences("CAMERA_PREF", 0)) as T
    }

}