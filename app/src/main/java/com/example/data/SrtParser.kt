package com.example.data

object SrtParser {
    fun parse(content: String, projectId: Int): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        // Normalize line endings
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split("\n\n")
        
        var indexCounter = 1
        for (block in blocks) {
            val lines = block.trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            
            // Find where timeline indicator "-->" lives
            var timelineIndex = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timelineIndex = i
                    break
                }
            }
            
            if (timelineIndex == -1) continue // No timeline found
            
            // Re-read index or fallback to sequence
            val indexVal = if (timelineIndex > 0) {
                lines[timelineIndex - 1].toIntOrNull() ?: indexCounter
            } else {
                indexCounter
            }
            
            val timeline = lines[timelineIndex]
            val parts = timeline.split("-->")
            if (parts.size < 2) continue
            val startMs = parseTimecode(parts[0].trim())
            val endMs = parseTimecode(parts[1].trim())
            
            val textLines = lines.subList(timelineIndex + 1, lines.size)
            val text = textLines.joinToString("\n")
            
            tracks.add(
                SubtitleTrack(
                    projectId = projectId,
                    indexNumber = indexVal,
                    startMs = startMs,
                    endMs = endMs,
                    originalText = text,
                    translatedText = ""
                )
            )
            indexCounter++
        }
        
        // Use line-by-line fallback if no tracks parsed (dense blocks)
        if (tracks.isEmpty()) {
            return parseLineByLineFallback(normalized, projectId)
        }
        
        return tracks
    }

    private fun parseLineByLineFallback(content: String, projectId: Int): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        val lines = content.split("\n").map { it.trim() }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                i++
                continue
            }
            
            val indexVal = line.toIntOrNull()
            if (indexVal != null) {
                if (i + 1 < lines.size) {
                    val timeLine = lines[i + 1]
                    if (timeLine.contains("-->")) {
                        val parts = timeLine.split("-->")
                        val startMs = parseTimecode(parts[0].trim())
                        val endMs = parseTimecode(parts[1].trim())
                        
                        val textLines = mutableListOf<String>()
                        var j = i + 2
                        while (j < lines.size) {
                            val nextLine = lines[j]
                            if (nextLine.isEmpty()) {
                                break
                            }
                            if (j + 1 < lines.size && nextLine.toIntOrNull() != null && lines[j+1].contains("-->")) {
                                break
                            }
                            textLines.add(nextLine)
                            j++
                        }
                        tracks.add(
                            SubtitleTrack(
                                projectId = projectId,
                                indexNumber = indexVal,
                                startMs = startMs,
                                endMs = endMs,
                                originalText = textLines.joinToString("\n"),
                                translatedText = ""
                            )
                        )
                        i = j
                        continue
                    }
                }
            }
            i++
        }
        return tracks
    }
    
    fun formatTimecode(ms: Long): String {
        val hours = ms / 3600000
        val mins = (ms % 3600000) / 60000
        val secs = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }

    private fun parseTimecode(tc: String): Long {
        try {
            val normalized = tc.replace('.', ',')
            val parts = normalized.split(",")
            val hms = parts[0].split(":")
            val h = hms[0].toLong()
            val m = hms[1].toLong()
            val s = hms[2].toLong()
            val ms = parts.getOrNull(1)?.toLong() ?: 0L
            return h * 3600000L + m * 60000L + s * 1000L + ms
        } catch (e: Exception) {
            return 0L
        }
    }
    
    fun exportToSrt(tracks: List<SubtitleTrack>, useKurdish: Boolean): String {
        val sb = java.lang.StringBuilder()
        tracks.forEach { track ->
            sb.append(track.indexNumber).append("\n")
            sb.append(formatTimecode(track.startMs))
              .append(" --> ")
              .append(formatTimecode(track.endMs))
              .append("\n")
            val text = if (useKurdish) {
                if (track.translatedText.isNotBlank()) track.translatedText else track.originalText
            } else {
                track.originalText
            }
            sb.append(text).append("\n\n")
        }
        return sb.toString()
    }
}
