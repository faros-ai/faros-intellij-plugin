package ai.faros.intellij.model

import java.util.Date

data class CodingEvent(
    var timestamp: Date = Date(),
    var charCountChange: Int = 0,
    var type: String = "",
    var filename: String = "",
    var extension: String = "",
    var language: String = "",
    var repository: String = "",
    var branch: String = ""
) 