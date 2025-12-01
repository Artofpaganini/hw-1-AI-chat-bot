package com.example.aiagentchat.api

enum class ApiProvider(val displayName: String, val requiresKey: Boolean) {
    QWEN("Qwen2.5 VL 72B Instruct", true),
    DEEPSEEK("DeepSeek (бесплатно)", true),
    GROQ("Groq (бесплатно, быстрый)", true),
    HUGGINGFACE("Hugging Face (бесплатно)", false),
    OLLAMA("Ollama (локально)", false),
    OPENAI("OpenAI (GPT-3.5)", true)
}
