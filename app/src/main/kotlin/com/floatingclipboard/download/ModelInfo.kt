package com.floatingclipboard.download

data class ModelInfo(
    val id: String,
    val displayName: String,
    val huggingFaceRepo: String,
    val fileName: String,
    val downloadSizeMb: Int,
    val requiredRamGb: Float,
    val requiredStorageGb: Float,
)

val TRANSLATION_MODELS = listOf(
    ModelInfo(
        id = "gemma4_e4b",
        displayName = "Gemma 4 E4B — najlepsza jakość",
        huggingFaceRepo = "litert-community/gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        downloadSizeMb = 2500,
        requiredRamGb = 8f,
        requiredStorageGb = 3.0f,
    ),
    ModelInfo(
        id = "qwen3_4b",
        displayName = "Qwen3 4B — dobra jakość",
        huggingFaceRepo = "litert-community/Qwen3-4B-it-litert-lm",
        fileName = "qwen3-4b-instruct-int4.litertlm",
        downloadSizeMb = 2500,
        requiredRamGb = 6f,
        requiredStorageGb = 3.0f,
    ),
    ModelInfo(
        id = "qwen3_600m",
        displayName = "Qwen3 0.6B — lekki, podstawowa jakość",
        huggingFaceRepo = "litert-community/Qwen3-0.6B-it-litert-lm",
        fileName = "qwen3-0.6b-instruct-int4.litertlm",
        downloadSizeMb = 500,
        requiredRamGb = 4f,
        requiredStorageGb = 0.6f,
    ),
)

val EMBEDDING_MODEL = ModelInfo(
    id = "embedding_gemma",
    displayName = "EmbeddingGemma 300M — scoring tłumaczeń",
    huggingFaceRepo = "litert-community/embeddinggemma-300m",
    fileName = "embeddinggemma-300M_seq512_mixed-precision.tflite",
    downloadSizeMb = 200,
    requiredRamGb = 0.2f,
    requiredStorageGb = 0.25f,
)

data class OnboardingRecommendation(
    val recommendedModel: ModelInfo?,
    val includeEmbedding: Boolean,
    val totalDownloadMb: Int,
)

fun recommendModels(ramGb: Float, freeStorageGb: Float): OnboardingRecommendation {
    val available = TRANSLATION_MODELS.filter { it.requiredRamGb <= ramGb }
    val recommended = available.firstOrNull {
        it.requiredStorageGb + EMBEDDING_MODEL.requiredStorageGb <= freeStorageGb
    }
    val embeddingFits = recommended != null &&
        recommended.requiredStorageGb + EMBEDDING_MODEL.requiredStorageGb <= freeStorageGb
    return OnboardingRecommendation(
        recommendedModel = recommended,
        includeEmbedding = embeddingFits,
        totalDownloadMb = (recommended?.downloadSizeMb ?: 0) +
            if (embeddingFits) EMBEDDING_MODEL.downloadSizeMb else 0,
    )
}
