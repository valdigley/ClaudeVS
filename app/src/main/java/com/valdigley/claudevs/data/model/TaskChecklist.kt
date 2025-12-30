package com.valdigley.claudevs.data.model

/**
 * Represents a single step/task in a checklist
 */
data class TaskStep(
    val id: Int,
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING
)

enum class TaskStatus {
    PENDING,      // Not started yet
    IN_PROGRESS,  // Currently being executed
    COMPLETED,    // Successfully completed
    FAILED        // Failed to complete
}

/**
 * Represents a checklist of tasks for a multi-step operation
 */
data class TaskChecklist(
    val title: String,
    val steps: List<TaskStep>,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var summary: String? = null
) {
    val isComplete: Boolean
        get() = steps.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }

    val completedCount: Int
        get() = steps.count { it.status == TaskStatus.COMPLETED }

    val progress: Float
        get() = if (steps.isEmpty()) 0f else completedCount.toFloat() / steps.size

    val currentStep: TaskStep?
        get() = steps.firstOrNull { it.status == TaskStatus.IN_PROGRESS }
            ?: steps.firstOrNull { it.status == TaskStatus.PENDING }

    fun withStepStatus(stepId: Int, status: TaskStatus): TaskChecklist {
        return copy(
            steps = steps.map {
                if (it.id == stepId) it.copy(status = status) else it
            }
        )
    }

    fun withNextStepInProgress(): TaskChecklist {
        val nextPending = steps.firstOrNull { it.status == TaskStatus.PENDING }
        return if (nextPending != null) {
            withStepStatus(nextPending.id, TaskStatus.IN_PROGRESS)
        } else this
    }

    fun withCurrentStepCompleted(): TaskChecklist {
        val current = steps.firstOrNull { it.status == TaskStatus.IN_PROGRESS }
        return if (current != null) {
            withStepStatus(current.id, TaskStatus.COMPLETED).withNextStepInProgress()
        } else this
    }
}

/**
 * Parser to extract task steps from Claude's output
 */
object TaskParser {

    // Patterns that indicate Claude is planning multi-step work
    private val planPatterns = listOf(
        Regex("""(?:vou|preciso|devo|irei)\s+(?:fazer|executar|implementar|criar).*?:?\s*\n""", RegexOption.IGNORE_CASE),
        Regex("""(?:steps?|passos?|etapas?|tarefas?).*?:?\s*\n""", RegexOption.IGNORE_CASE),
        Regex("""(?:primeiro|1\.|1\)|\[1\])""", RegexOption.IGNORE_CASE)
    )

    // Patterns to extract individual steps
    private val stepPatterns = listOf(
        Regex("""^\s*(\d+)[.)\]]\s*(.+)$""", RegexOption.MULTILINE),
        Regex("""^\s*[-•*]\s*(.+)$""", RegexOption.MULTILINE),
        Regex("""^\s*(?:primeiro|segundo|terceiro|quarto|quinto)[,:]?\s*(.+)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    )

    // Patterns that indicate task completion
    private val completionPatterns = listOf(
        Regex("""(?:concluído|completo|feito|pronto|finalizado|implementado|criado|atualizado)""", RegexOption.IGNORE_CASE),
        Regex("""✅|✓|☑️"""),
        Regex("""(?:successfully|completed|done|finished)""", RegexOption.IGNORE_CASE)
    )

    /**
     * Detect if text contains a multi-step plan
     */
    fun containsPlan(text: String): Boolean {
        return planPatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Extract steps from Claude's output
     */
    fun extractSteps(text: String): List<String> {
        val steps = mutableListOf<String>()

        // Try numbered steps first (1. 2. 3. or 1) 2) 3))
        val numberedMatches = stepPatterns[0].findAll(text)
        for (match in numberedMatches) {
            val step = match.groupValues.getOrNull(2)?.trim()
            if (!step.isNullOrBlank() && step.length > 5) {
                steps.add(step)
            }
        }

        if (steps.isNotEmpty()) return steps

        // Try bullet points
        val bulletMatches = stepPatterns[1].findAll(text)
        for (match in bulletMatches) {
            val step = match.groupValues.getOrNull(1)?.trim()
            if (!step.isNullOrBlank() && step.length > 5) {
                steps.add(step)
            }
        }

        return steps
    }

    /**
     * Create a TaskChecklist from Claude's planning output
     */
    fun createChecklist(title: String, text: String): TaskChecklist? {
        val steps = extractSteps(text)
        if (steps.size < 2) return null // Only create checklist for multi-step tasks

        return TaskChecklist(
            title = title,
            steps = steps.mapIndexed { index, desc ->
                TaskStep(id = index, description = desc)
            }
        )
    }

    /**
     * Check if text indicates a step completion
     */
    fun indicatesCompletion(text: String): Boolean {
        return completionPatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Generate a summary of completed tasks
     */
    fun generateSummary(checklist: TaskChecklist): String {
        val completed = checklist.steps.filter { it.status == TaskStatus.COMPLETED }
        val failed = checklist.steps.filter { it.status == TaskStatus.FAILED }

        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("📋 RESUMO: ${checklist.title}")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()

        if (completed.isNotEmpty()) {
            sb.appendLine("✅ Concluído (${completed.size}/${checklist.steps.size}):")
            completed.forEach { step ->
                sb.appendLine("   • ${step.description}")
            }
        }

        if (failed.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("❌ Falhou:")
            failed.forEach { step ->
                sb.appendLine("   • ${step.description}")
            }
        }

        val duration = checklist.endTime?.let { end ->
            val seconds = (end - checklist.startTime) / 1000
            if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
        } ?: "em andamento"

        sb.appendLine()
        sb.appendLine("⏱️ Tempo: $duration")
        sb.appendLine("═══════════════════════════════════")

        return sb.toString()
    }
}
