package com.animelib.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animelib.app.data.db.Favorite
import com.animelib.app.data.db.FavoriteDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    favoriteDao: FavoriteDao,
) : ViewModel() {

    val favorites: StateFlow<List<Favorite>> = favoriteDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow("all") // all / watching / planned / completed
    val filter: StateFlow<String> = _filter.asStateFlow()

    fun setFilter(f: String) { _filter.value = f }
}
