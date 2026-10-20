package com.astron.app.ui.screens.filebrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferItem
import com.astron.app.core.storage.BrowserFileItem
import com.astron.app.core.storage.FileBrowserManager
import com.astron.app.core.storage.FileCategory
import com.astron.app.core.storage.FileSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FileBrowserUiState(
    val selectedCategory: FileCategory = FileCategory.RECENT,
    val items: List<BrowserFileItem> = emptyList(),
    val selectedItems: Set<BrowserFileItem> = emptySet(),
    val searchQuery: String = "",
    val sortOrder: FileSortOrder = FileSortOrder.DATE_DESC,
    val isLoading: Boolean = false,
    val previewItem: TransferItem? = null
) {
    val totalSelectedBytes: Long get() = selectedItems.sumOf { it.size }
    val totalSelectedCount: Int get() = selectedItems.size
}

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val fileBrowserManager: FileBrowserManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    init {
        loadFiles()
    }

    fun selectCategory(category: FileCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        loadFiles()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadFiles()
    }

    fun updateSortOrder(order: FileSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        loadFiles()
    }

    fun toggleSelection(item: BrowserFileItem) {
        _uiState.update { current ->
            val updated = if (current.selectedItems.contains(item)) {
                current.selectedItems - item
            } else {
                current.selectedItems + item
            }
            current.copy(selectedItems = updated)
        }
    }

    fun selectAll() {
        _uiState.update { current ->
            current.copy(selectedItems = current.selectedItems + current.items)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptySet()) }
    }

    fun openPreview(item: BrowserFileItem) {
        _uiState.update { it.copy(previewItem = item.toTransferItem()) }
    }

    fun closePreview() {
        _uiState.update { it.copy(previewItem = null) }
    }

    fun getSelectedTransferItems(): List<TransferItem> {
        return _uiState.value.selectedItems.map { it.toTransferItem() }
    }

    fun reload() {
        loadFiles()
    }

    private fun loadFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val current = _uiState.value
            val list = fileBrowserManager.loadCategoryFiles(
                category = current.selectedCategory,
                searchQuery = current.searchQuery,
                sortOrder = current.sortOrder
            )
            _uiState.update { it.copy(items = list, isLoading = false) }
        }
    }
}
