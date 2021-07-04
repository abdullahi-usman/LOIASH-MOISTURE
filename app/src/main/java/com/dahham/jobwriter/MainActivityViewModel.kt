package com.dahham.jobwriter

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel(val sharedPreferences: SharedPreferences): ViewModel() {

    private var _isLegacyMode = MutableLiveData(false)

    var isLegacyMode: LiveData<Boolean> = _isLegacyMode

    init {
        _isLegacyMode.value = sharedPreferences.getBoolean("LEGACY_MODE", false)
    }

    fun setLegacyMode(modeLegacy: Boolean){
        sharedPreferences.edit().putBoolean("LEGACY_MODE", modeLegacy).apply()
        _isLegacyMode.value = modeLegacy
    }

}