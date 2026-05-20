package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class SubtitleRepository(private val subtitleDao: SubtitleDao) {
    val allProjects: Flow<List<SubtitleProject>> = subtitleDao.getAllProjects()

    fun getTracksForProject(projectId: Int): Flow<List<SubtitleTrack>> {
        return subtitleDao.getTracksForProject(projectId)
    }

    suspend fun getTracksForProjectSync(projectId: Int): List<SubtitleTrack> {
        return subtitleDao.getTracksForProjectSync(projectId)
    }

    suspend fun getProjectById(id: Int): SubtitleProject? {
        return subtitleDao.getProjectById(id)
    }

    suspend fun createProject(name: String, sourceFileName: String, srtContent: String): Long {
        val project = SubtitleProject(name = name, sourceFileName = sourceFileName)
        val projectId = subtitleDao.insertProject(project)
        val tracks = SrtParser.parse(srtContent, projectId.toInt())
        subtitleDao.insertTracks(tracks)
        return projectId
    }

    suspend fun updateTrack(track: SubtitleTrack) {
        subtitleDao.updateTrack(track)
    }

    suspend fun updateTracks(tracks: List<SubtitleTrack>) {
        subtitleDao.updateTracks(tracks)
    }

    suspend fun deleteProject(projectId: Int) {
        subtitleDao.deleteProjectById(projectId)
    }
    
    suspend fun seedInitialData() {
        val existing = allProjects.firstOrNull()
        if (existing.isNullOrEmpty()) {
            val projectId = createProject(
                name = "Kurdish Nature Trailer (Demo)",
                sourceFileName = "nature_trailer.en.srt",
                srtContent = """
1
00:00:01,200 --> 00:00:04,500
[Dramatic Orchestral Music]
In a land where mountains speak of history...

2
00:00:05,100 --> 00:00:08,200
♪ Majestic ambient melody ♪
Our journey begins in the heart of Kurdistan.

3
00:00:09,000 --> 00:00:13,800
(Voices whispering)
We must preserve the ancient lore for future generations.

4
00:00:15,000 --> 00:00:18,500
[Laughter]
They said it was impossible, but we did it anyway.

5
00:00:19,200 --> 00:00:22,000
Join us this September.
Only in theaters.
""".trimIndent()
            )
            
            // Let's seed some sample Kurdish translation work
            val tracks = subtitleDao.getTracksForProjectSync(projectId.toInt())
            if (tracks.size >= 5) {
                subtitleDao.updateTrack(tracks[0].copy(translatedText = "لە خاکێکدا کە چیاکان باسی مێژوو دەکەن..."))
                subtitleDao.updateTrack(tracks[1].copy(translatedText = "گەشتەکەمان لە دڵی کوردستانەوە دەست پێدەکات."))
                subtitleDao.updateTrack(tracks[2].copy(translatedText = "دەبێت زانیارییە دێرینەکان بپارێزین بۆ نەوەکانی داهاتوو."))
                subtitleDao.updateTrack(tracks[3].copy(translatedText = "وتویانە مەحاڵە، بەڵام ئێمە هەر ئەنجاممان دا."))
                subtitleDao.updateTrack(tracks[4].copy(translatedText = "لە مانگی ئەیلوولدا پەیوەندیمان پێوە بکەن. تەنها لە سینەماکان."))
            }
        }
    }
}
