package com.divinegames.mmover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StartViewModel : ViewModel() {

    // В этой переменной мы будем хранить состояние "готово ли приложение"
    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    // Длительность задержки в миллисекундах (3000L = 3 секунды)
    private companion object {
        const val SPLASH_DELAY = 2000L
    }

    init {
        // Запускаем корутину, которая ждет нужное время
        viewModelScope.launch {
            delay(SPLASH_DELAY)
            // После задержки сообщаем, что приложение "готово"
            _isReady.value = true
        }
    }
}