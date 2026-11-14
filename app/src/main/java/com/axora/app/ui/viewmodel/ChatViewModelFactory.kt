package com.axora.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.axora.app.data.repository.ChatRepository

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(repository, ContextStringResolver(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}