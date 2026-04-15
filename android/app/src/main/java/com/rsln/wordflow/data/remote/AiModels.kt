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
        val AVAILABLE_MODELS = listOf(
            AiModel("google/gemini-2.0-flash-001", "Gemini 2.0 Flash"),
            AiModel("google/gemini-2.5-flash-preview", "Gemini 2.5 Flash"),
            AiModel("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku"),
            AiModel("anthropic/claude-sonnet-4", "Claude Sonnet 4"),
            AiModel("openai/gpt-4o-mini", "GPT-4o Mini"),
            AiModel("openai/gpt-4o", "GPT-4o"),
            AiModel("deepseek/deepseek-chat-v3-0324", "DeepSeek V3"),
        )
    }
}
