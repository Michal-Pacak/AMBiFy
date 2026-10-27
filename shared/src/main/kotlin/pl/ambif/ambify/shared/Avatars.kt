package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/** Shapes in the order they are handed out to people with the same initials in one community. */
@Serializable
enum class AvatarShape { CIRCLE, SQUARE, TRIANGLE, STAR, HEXAGON, DIAMOND }

/**
 * A person's icon in one community: initials on a color, in a shape. The server hands it out once and keeps it,
 * so the same person looks the same in every module of that community.
 */
@Serializable
data class Avatar(val initials: String, val color: String, val shape: AvatarShape)

object Avatars {
    /** Member colors, given out least-used first; minors keep the color chosen for them. */
    val PALETTE = listOf("#8EC3F0", "#E3A8C0", "#7FD3CF", "#B9A7EE", "#F2C27B", "#9AD7A4", "#F29C8B", "#C9C9CF")

    /** First letters of the first and last word ("Anna Maria Kowalska" → "AK"); a single word gives its first two letters. */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val letters = when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2)
            else -> "${words.first().first()}${words.last().first()}"
        }
        return letters.uppercase()
    }

    /** The first shape nobody with the same initials uses yet; when all are taken the color tells them apart. */
    fun freeShape(taken: Collection<AvatarShape>): AvatarShape =
        AvatarShape.entries.firstOrNull { it !in taken } ?: AvatarShape.CIRCLE

    /** The palette color used the fewest times so far (the earliest one on a tie). */
    fun freeColor(used: Collection<String>): String =
        PALETTE.minBy { color -> used.count { it.equals(color, ignoreCase = true) } }
}
