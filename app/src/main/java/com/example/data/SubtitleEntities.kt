package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_projects")
data class SubtitleProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sourceFileName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subtitle_tracks",
    foreignKeys = [
        ForeignKey(
            entity = SubtitleProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId", "indexNumber"])]
)
data class SubtitleTrack(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val indexNumber: Int,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String
)
