package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SrtParser
import com.example.data.SubtitleProject
import com.example.data.SubtitleRepository
import com.example.data.SubtitleTrack
import com.example.data.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FilterMode {
    ALL,
    UNTRANSLATED,
    TRANSLATED
}

class SubtitleViewModel(
    application: Application,
    private val repository: SubtitleRepository
) : AndroidViewModel(application) {

    // Projects list
    val projects = repository.allProjects

    // Active project selection
    private val _selectedProjectId = MutableStateFlow<Int?>(null)
    val selectedProjectId = _selectedProjectId.asStateFlow()

    private val _selectedProject = MutableStateFlow<SubtitleProject?>(null)
    val selectedProject = _selectedProject.asStateFlow()

    // Loaded tracks for continuous inline editing
    private val _loadedTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val loadedTracks = _loadedTracks.asStateFlow()

    // Comma-separated query string
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Filter mode
    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode = _filterMode.asStateFlow()

    // Custom API Key Override
    private val _apiKeyOverride = MutableStateFlow("")
    val apiKeyOverride = _apiKeyOverride.asStateFlow()

    // Selected/Active track ID
    private val _activeTrackId = MutableStateFlow<Int?>(null)
    val activeTrackId = _activeTrackId.asStateFlow()

    // Find and Replace
    private val _findQuery = MutableStateFlow("")
    val findQuery = _findQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery = _replaceQuery.asStateFlow()

    // Status banner feedback
    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback = _actionFeedback.asStateFlow()

    // Gemini connection / progress states
    private val _isProcessingGemini = MutableStateFlow(false)
    val isProcessingGemini = _isProcessingGemini.asStateFlow()

    private val _activeTaskTrackId = MutableStateFlow<Int?>(null)
    val activeTaskTrackId = _activeTaskTrackId.asStateFlow()

    // Navigation scroll triggers
    private val _scrollRequest = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollRequest: SharedFlow<Int> = _scrollRequest.asSharedFlow()

    private var observerJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.seedInitialData()
            
            // Auto select the first project if available
            projects.collectLatest { list ->
                if (_selectedProjectId.value == null && list.isNotEmpty()) {
                    selectProject(list.first().id)
                }
            }
        }
    }

    fun selectProject(projectId: Int) {
        _selectedProjectId.value = projectId
        
        // Cancel previous track observer
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            _selectedProject.value = repository.getProjectById(projectId)
            
            // Fetch tracks
            repository.getTracksForProject(projectId).collect { list ->
                val current = _loadedTracks.value
                val isStructuralChange = current.size != list.size || 
                                         current.any { c -> list.none { l -> l.id == c.id } }
                
                if (isStructuralChange) {
                    _loadedTracks.value = list
                    if (list.isNotEmpty() && _activeTrackId.value == null) {
                        _activeTrackId.value = list.first().id
                    }
                }
            }
        }
        clearFeedback()
    }

    // Setters for queries & custom api key override
    fun setApiKeyOverride(key: String) {
        _apiKeyOverride.value = key
    }

    fun setFilterMode(mode: FilterMode) {
        _filterMode.value = mode
    }

    // Interactive in-line editing updates
    fun updateTrackContent(trackId: Int, original: String, translated: String) {
        val updated = _loadedTracks.value.map { track ->
            if (track.id == trackId) {
                track.copy(originalText = original, translatedText = translated)
            } else {
                track
            }
        }
        _loadedTracks.value = updated
        _activeTrackId.value = trackId

        // Schedule Room database update (debounced)
        scheduleAutoSave(updated)
    }

    private fun scheduleAutoSave(tracks: List<SubtitleTrack>) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1000)
            repository.updateTracks(tracks)
        }
    }

    fun commitImmediateChanges() {
        autoSaveJob?.cancel()
        val current = _loadedTracks.value
        if (current.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateTracks(current)
            }
        }
    }

    fun selectTrack(trackId: Int) {
        _activeTrackId.value = trackId
    }

    // Search query support
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFindQuery(query: String) {
        _findQuery.value = query
    }

    fun setReplaceQuery(query: String) {
        _replaceQuery.value = query
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    // Kurdish translating & refining logic via Gemini
    fun translateTrack(trackId: Int) {
        val track = _loadedTracks.value.find { it.id == trackId } ?: return
        if (track.originalText.isBlank()) return

        viewModelScope.launch {
            _isProcessingGemini.value = true
            _activeTaskTrackId.value = trackId
            _actionFeedback.value = "Translating sentence #${track.indexNumber} to Kurdish Sorani..."
            
            val service = GeminiService(_apiKeyOverride.value)
            val result = withContext(Dispatchers.IO) {
                service.translateToKurdishSorani(track.originalText)
            }
            
            result.onSuccess { translation ->
                updateTrackContent(trackId, track.originalText, translation)
                _actionFeedback.value = "Kurdish translation complete for sentence #${track.indexNumber}."
            }.onFailure { error ->
                _actionFeedback.value = "Error: ${error.message}"
            }
            
            _isProcessingGemini.value = false
            _activeTaskTrackId.value = null
        }
    }

    fun refineTrack(trackId: Int) {
        val track = _loadedTracks.value.find { it.id == trackId } ?: return
        val existingTranslation = track.translatedText
        if (existingTranslation.isBlank()) {
            _actionFeedback.value = "Cannot refine. Translate this sentence first!"
            return
        }

        viewModelScope.launch {
            _isProcessingGemini.value = true
            _activeTaskTrackId.value = trackId
            _actionFeedback.value = "Refining sentence #${track.indexNumber} translation..."
            
            val service = GeminiService(_apiKeyOverride.value)
            val result = withContext(Dispatchers.IO) {
                service.refineKurdishSorani(existingTranslation, track.originalText)
            }
            
            result.onSuccess { refinedTranslation ->
                updateTrackContent(trackId, track.originalText, refinedTranslation)
                _actionFeedback.value = "Sorani refinement complete for sentence #${track.indexNumber}."
            }.onFailure { error ->
                _actionFeedback.value = "Error: ${error.message}"
            }
            
            _isProcessingGemini.value = false
            _activeTaskTrackId.value = null
        }
    }

    fun translateAllUntranslated() {
        val untranslated = _loadedTracks.value.filter { it.translatedText.isBlank() && it.originalText.isNotBlank() }
        if (untranslated.isEmpty()) {
            _actionFeedback.value = "All tracks already have Kurdish translations!"
            return
        }

        viewModelScope.launch {
            _isProcessingGemini.value = true
            var count = 0
            val service = GeminiService(_apiKeyOverride.value)
            
            for (track in untranslated) {
                _activeTaskTrackId.value = track.id
                _actionFeedback.value = "Batch translating sentence ${count + 1}/${untranslated.size}..."
                
                val result = withContext(Dispatchers.IO) {
                    service.translateToKurdishSorani(track.originalText)
                }
                
                result.onSuccess { translation ->
                    updateTrackContent(track.id, track.originalText, translation)
                    count++
                }.onFailure { error ->
                    _actionFeedback.value = "Batch translation paused after $count successes. Error: ${error.message}"
                    _isProcessingGemini.value = false
                    _activeTaskTrackId.value = null
                    return@launch
                }
                
                delay(400) // Small delay to play well with rate limits
            }
            
            _actionFeedback.value = "Batch translation complete! Translated $count subtitle lines."
            _isProcessingGemini.value = false
            _activeTaskTrackId.value = null
        }
    }

    fun refineAllTranslated() {
        val translated = _loadedTracks.value.filter { it.translatedText.isNotBlank() }
        if (translated.isEmpty()) {
            _actionFeedback.value = "No translated subtitles to refine!"
            return
        }

        viewModelScope.launch {
            _isProcessingGemini.value = true
            var count = 0
            val service = GeminiService(_apiKeyOverride.value)
            
            for (track in translated) {
                _activeTaskTrackId.value = track.id
                _actionFeedback.value = "Batch refining sentence ${count + 1}/${translated.size} to elite Sorani..."
                
                val result = withContext(Dispatchers.IO) {
                    service.refineKurdishSorani(track.translatedText, track.originalText)
                }
                
                result.onSuccess { refined ->
                    updateTrackContent(track.id, track.originalText, refined)
                    count++
                }.onFailure { error ->
                    _actionFeedback.value = "Batch refinement paused after $count successes. Error: ${error.message}"
                    _isProcessingGemini.value = false
                    _activeTaskTrackId.value = null
                    return@launch
                }
                
                delay(400) // rate limit spacer
            }
            
            _actionFeedback.value = "Batch refinement complete! Polished $count subtitle lines."
            _isProcessingGemini.value = false
            _activeTaskTrackId.value = null
        }
    }

    // Relevance scoring & filtering for multi-modes
    fun getFilteredTracks(): List<SubtitleTrack> {
        val tracks = _loadedTracks.value
        val rawQuery = _searchQuery.value
        val mode = _filterMode.value

        // 1. Filter by mode (All, Untranslated, Translated)
        val modeFiltered = when (mode) {
            FilterMode.ALL -> tracks
            FilterMode.UNTRANSLATED -> tracks.filter { it.translatedText.isBlank() }
            FilterMode.TRANSLATED -> tracks.filter { it.translatedText.isNotBlank() }
        }

        // 2. Filter by search text query
        if (rawQuery.isBlank()) {
            return modeFiltered.sortedBy { it.indexNumber }
        }

        val keywords = rawQuery.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        if (keywords.isEmpty()) {
            return modeFiltered.sortedBy { it.indexNumber }
        }

        val filtered = modeFiltered.filter { track ->
            keywords.all { kw ->
                track.originalText.lowercase().contains(kw) ||
                track.translatedText.lowercase().contains(kw) ||
                track.indexNumber.toString() == kw
            }
        }

        return filtered.sortedWith(
            compareByDescending<SubtitleTrack> { computeTrackRelevance(it, keywords) }
                .thenBy { it.indexNumber }
        )
    }

    private fun computeTrackRelevance(track: SubtitleTrack, keywords: List<String>): Int {
        var score = 0
        for (kw in keywords) {
            val orig = track.originalText.lowercase()
            val trans = track.translatedText.lowercase()

            val exactWordRegex = Regex("\\b${Regex.escape(kw)}\\b")
            val isExactOrig = exactWordRegex.containsMatchIn(orig)
            val isExactTrans = exactWordRegex.containsMatchIn(trans)

            if (isExactOrig) score += 20
            if (isExactTrans) score += 20

            if (orig == kw) score += 100
            if (trans == kw) score += 100

            if (orig.contains(kw) && !isExactOrig) score += 5
            if (trans.contains(kw) && !isExactTrans) score += 5
        }
        return score
    }

    // Edit functions
    fun performReplaceNext() {
        val find = _findQuery.value
        val replaceWith = _replaceQuery.value
        if (find.isEmpty()) {
            _actionFeedback.value = "Please enter text to find."
            return
        }

        val tracks = _loadedTracks.value
        if (tracks.isEmpty()) return

        val activeIndex = tracks.indexOfFirst { it.id == _activeTrackId.value }
        val startIndex = if (activeIndex != -1) activeIndex + 1 else 0

        var foundIndex = -1
        for (i in startIndex until tracks.size) {
            if (tracks[i].originalText.contains(find) || tracks[i].translatedText.contains(find)) {
                foundIndex = i
                break
            }
        }

        if (foundIndex == -1) {
            for (i in 0 until startIndex) {
                if (tracks[i].originalText.contains(find) || tracks[i].translatedText.contains(find)) {
                    foundIndex = i
                    break
                }
            }
        }

        if (foundIndex != -1) {
            val trackMatch = tracks[foundIndex]
            val newOriginal = trackMatch.originalText.replaceFirst(find, replaceWith)
            val newTranslated = trackMatch.translatedText.replaceFirst(find, replaceWith)
            
            val updatedTrack = trackMatch.copy(originalText = newOriginal, translatedText = newTranslated)
            val newList = tracks.map { if (it.id == trackMatch.id) updatedTrack else it }
            _loadedTracks.value = newList
            _activeTrackId.value = updatedTrack.id
            _scrollRequest.tryEmit(foundIndex)
            
            _actionFeedback.value = "Replaced first match at track #${updatedTrack.indexNumber}."
            scheduleAutoSave(newList)
        } else {
            _actionFeedback.value = "No match found for '$find'."
        }
    }

    fun performReplaceAll() {
        val find = _findQuery.value
        val replaceWith = _replaceQuery.value
        if (find.isEmpty()) {
            _actionFeedback.value = "Please enter text to find."
            return
        }

        val tracks = _loadedTracks.value
        var occurrenceCount = 0

        val updated = tracks.map { track ->
            val origCount = track.originalText.split(find).size - 1
            val transCount = track.translatedText.split(find).size - 1
            val matchCount = origCount.coerceAtLeast(0) + transCount.coerceAtLeast(0)
            occurrenceCount += matchCount
            
            if (matchCount > 0) {
                track.copy(
                    originalText = track.originalText.replace(find, replaceWith),
                    translatedText = track.translatedText.replace(find, replaceWith)
                )
            } else {
                track
            }
        }

        if (occurrenceCount > 0) {
            _loadedTracks.value = updated
            _actionFeedback.value = "Successfully replaced $occurrenceCount occurrences."
            scheduleAutoSave(updated)
        } else {
            _actionFeedback.value = "No matches found for '$find'."
        }
    }

    fun performMasterCleanUp() {
        val sdhRegex = Regex("\\[[^]]*\\]|\\([^)]*\\)|[♪♫♩]")
        val currentTracks = _loadedTracks.value

        val cleaned = currentTracks.mapNotNull { track ->
            var cleanOriginal = sdhRegex.replace(track.originalText, "")
            var cleanTranslated = sdhRegex.replace(track.translatedText, "")

            cleanOriginal = cleanOriginal.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .trim()

            cleanTranslated = cleanTranslated.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .trim()

            val hasContent = cleanOriginal.any { it.isLetterOrDigit() }
            if (!hasContent) null else track.copy(originalText = cleanOriginal, translatedText = cleanTranslated)
        }.mapIndexed { listIndex, track ->
            track.copy(indexNumber = listIndex + 1)
        }

        _loadedTracks.value = cleaned
        _actionFeedback.value = "Master Clean Up completed. ${currentTracks.size - cleaned.size} empty frames purged and reindexed."
        
        if (cleaned.isNotEmpty() && cleaned.none { it.id == _activeTrackId.value }) {
            _activeTrackId.value = cleaned.first().id
        }

        scheduleAutoSave(cleaned)
    }

    fun addNewTrack() {
        val tracks = _loadedTracks.value
        val projectId = _selectedProjectId.value ?: return

        var insertIndex = tracks.size
        var startMs = 0L
        var endMs = 3000L

        val activeTrack = tracks.find { it.id == _activeTrackId.value }
        if (activeTrack != null) {
            insertIndex = tracks.indexOf(activeTrack) + 1
            startMs = activeTrack.endMs + 100L
            endMs = startMs + 3000L
        } else if (tracks.isNotEmpty()) {
            val last = tracks.last()
            startMs = last.endMs + 100L
            endMs = startMs + 3000L
        }

        val newTrack = SubtitleTrack(
            projectId = projectId,
            indexNumber = 1,
            startMs = startMs,
            endMs = endMs,
            originalText = "[New subtitle original dialogue]",
            translatedText = ""
        )

        val updatedList = tracks.toMutableList()
        if (insertIndex in 0..tracks.size) {
            updatedList.add(insertIndex, newTrack)
        } else {
            updatedList.add(newTrack)
        }

        val finalTracks = updatedList.mapIndexed { i, track ->
            track.copy(indexNumber = i + 1)
        }

        _loadedTracks.value = finalTracks
        _activeTrackId.value = newTrack.id
        _actionFeedback.value = "Added new dialogue slot at sequence position #${insertIndex + 1}."

        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTracks(finalTracks)
            val refreshed = repository.getTracksForProjectSync(projectId)
            if (refreshed.isNotEmpty()) {
                _loadedTracks.value = refreshed
                val match = refreshed.getOrNull(insertIndex) ?: refreshed.last()
                _activeTrackId.value = match.id
            }
        }
    }

    fun deleteTrack(trackId: Int) {
        val tracks = _loadedTracks.value
        val toDelete = tracks.find { it.id == trackId } ?: return

        val remaining = tracks.filter { it.id != trackId }
        val reordered = remaining.mapIndexed { index, track ->
            track.copy(indexNumber = index + 1)
        }

        _loadedTracks.value = reordered
        _actionFeedback.value = "Deleted track #${toDelete.indexNumber}."

        if (reordered.isNotEmpty() && _activeTrackId.value == trackId) {
            val nextFocusIndex = tracks.indexOf(toDelete).coerceAtMost(reordered.size - 1)
            _activeTrackId.value = reordered[nextFocusIndex].id
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTracks(reordered)
            AppDatabase.getDatabase(getApplication()).subtitleDao().deleteTracksForProject(toDelete.projectId)
            AppDatabase.getDatabase(getApplication()).subtitleDao().insertTracks(reordered)
        }
    }

    fun prepareExportContent(useKurdish: Boolean): Pair<String, String> {
        commitImmediateChanges()
        val project = _selectedProject.value ?: return "untitled.ku.srt" to ""
        val tracks = _loadedTracks.value
        
        val sourceName = project.sourceFileName
        val baseWithoutExtension = if (sourceName.contains(".")) {
            sourceName.substringBeforeLast(".")
        } else {
            sourceName
        }

        val cleanBaseName = baseWithoutExtension.replace(Regex("\\.(en|EN|fr|FR|de|DE|es|ES|ar|AR)$"), "")
        val finalFileName = "$cleanBaseName.ku.srt"

        val formattedSrtText = SrtParser.exportToSrt(tracks, useKurdish)
        return finalFileName to formattedSrtText
    }

    fun importProject(projectName: String, fileName: String, srtText: String) {
        viewModelScope.launch {
            val projectId = repository.createProject(projectName, fileName, srtText)
            selectProject(projectId.toInt())
            _actionFeedback.value = "Imported subtitle '$projectName' successfully."
        }
    }

    fun deleteProject(projectId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProject(projectId)
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
                _selectedProject.value = null
                _loadedTracks.value = emptyList()
                _activeTrackId.value = null
            }
        }
    }
}

class SubtitleViewModelFactory(
    private val application: Application,
    private val repository: SubtitleRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubtitleViewModel::class.java)) {
            return SubtitleViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
