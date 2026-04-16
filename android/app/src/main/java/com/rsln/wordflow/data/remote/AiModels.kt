package com.rsln.wordflow.data.remote

data class AiTranslationRequest(
    val words: List<String>,
    val existingCollections: List<String>
)

data class AiTranslationResult(
    val translation: String = "",
    val examples: List<String> = emptyList(),
    val pronunciation: String = "",
    val reason: String = "",
    val suggestion: String = "",
    val suggestionTranslation: String = "",
    val matchedCollections: List<String> = emptyList(),
    val suggestedCollections: List<String> = emptyList(),
    val difficulty: Int = 5
)

data class AiModel(
    val id: String,
    val name: String
) {
    companion object {
        const val DEFAULT_MODEL_ID = "google/gemini-2.5-flash-lite"

        val AVAILABLE_MODELS = listOf(
            AiModel("google/gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
            AiModel("openai/gpt-4.1-mini", "GPT-4.1 Mini"),
            AiModel("openai/gpt-4o-mini", "GPT-4o Mini"),
            AiModel("deepseek/deepseek-chat-v3-0324", "DeepSeek V3"),
            AiModel("anthropic/claude-haiku-4.5", "Claude Haiku 4.5")
        )

        fun isSupported(id: String): Boolean = AVAILABLE_MODELS.any { it.id == id }

        fun normalize(id: String): String =
            id.takeIf { isSupported(it) } ?: DEFAULT_MODEL_ID

        fun displayName(id: String): String =
            AVAILABLE_MODELS.firstOrNull { it.id == id }?.name
                ?: AVAILABLE_MODELS.first { it.id == DEFAULT_MODEL_ID }.name
    }
}
