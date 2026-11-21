package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.axora.app.data.repository.TemplateStore
import com.axora.app.domain.model.PromptTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TemplateViewModel(private val store: TemplateStore) : ViewModel() {

    private val _templates = MutableStateFlow<List<PromptTemplate>>(emptyList())
    val templates: StateFlow<List<PromptTemplate>> = _templates.asStateFlow()

    init {
        viewModelScope.launch {
            _templates.value = withContext(Dispatchers.IO) { store.load() }
        }
    }

    fun addTemplate(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val updated = _templates.value + PromptTemplate(
            id = System.currentTimeMillis(),
            text = trimmed
        )
        _templates.value = updated
        persist(updated)
    }

    fun deleteTemplate(id: Long) {
        val updated = _templates.value.filterNot { it.id == id }
        _templates.value = updated
        persist(updated)
    }

    private fun persist(templates: List<PromptTemplate>) {
        viewModelScope.launch(Dispatchers.IO) { store.save(templates) }
    }
}

class TemplateViewModelFactory(private val store: TemplateStore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TemplateViewModel(store) as T
}
