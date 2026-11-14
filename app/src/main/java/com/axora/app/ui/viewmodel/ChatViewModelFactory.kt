package com.axora.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.axora.app.data.repository.ChatRepository

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(repository, ContextStringResolver(application)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}