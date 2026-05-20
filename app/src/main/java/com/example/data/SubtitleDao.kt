package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitle_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<SubtitleProject>>

    @Query("SELECT * FROM subtitle_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Int): SubtitleProject?

    @Query("SELECT * FROM subtitle_tracks WHERE projectId = :projectId ORDER BY indexNumber ASC")
    fun getTracksForProject(projectId: Int): Flow<List<SubtitleTrack>>

    @Query("SELECT * FROM subtitle_tracks WHERE projectId = :projectId ORDER BY indexNumber ASC")
    suspend fun getTracksForProjectSync(projectId: Int): List<SubtitleTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: SubtitleProject): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: SubtitleTrack): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<SubtitleTrack>)

    @Update
    suspend fun updateTrack(track: SubtitleTrack)

    @Update
    suspend fun updateTracks(tracks: List<SubtitleTrack>)

    @Query("DELETE FROM subtitle_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Int)

    @Query("DELETE FROM subtitle_tracks WHERE projectId = :projectId")
    suspend fun deleteTracksForProject(projectId: Int)
}
