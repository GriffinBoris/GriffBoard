package com.griffinboris.griffboard.models

data class WhisperModel(
    val id: String,
    val label: String,
    val description: String,
    val bytes: Long,
    val sha256: String,
    val revision: String = "87cd18b47b941d2f65d09981dad23bb7d0481c77",
) {
    val filename get() = "ggml-$id.bin"
    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/$revision/$filename"
    val englishOnly get() = ".en" in id
    val sizeLabel get() = "${bytes / 1_000_000} MB"
}

object ModelCatalog {
    val models = listOf(
        WhisperModel("tiny.en-q5_1", "Tiny · English", "Quick notes · lowest memory use", 32_166_155,
            "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b"),
        WhisperModel("base.en-q5_1", "Base · English", "A good starting point for everyday dictation", 59_721_011,
            "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f"),
        WhisperModel("base-q5_1", "Base · Multilingual", "Compact model with language detection", 59_707_625,
            "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"),
        WhisperModel("small-q5_1", "Small · Multilingual", "More accuracy, more processing time", 190_085_487,
            "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"),
        WhisperModel("medium-q5_0", "Medium · Multilingual", "High memory use · slower on phones", 539_212_467,
            "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f"),
        WhisperModel("large-v3-turbo-q5_0", "Large v3 Turbo", "High memory use · try after Base or Small", 574_041_195,
            "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
            "98aa99a0a9db05ae2342309f5096248665f7cba3"),
    )
    fun find(id: String) = models.first { it.id == id }
}
