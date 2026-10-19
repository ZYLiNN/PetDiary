package com.zylinn.petdiary.data.pet

/**
 * The colour a pet is drawn with on the calendar. Stored on the pet by name; the actual shades (light / dark theme)
 * live in the UI layer. Order = the order new pets are given colours in.
 */
enum class PetColor(val label: String) {
    ORANGE("橘色"), WHITE("白色"), BLUE("藍色"), PINK("粉紅"), GREEN("綠色"), PURPLE("紫色"), YELLOW("黃色"), GRAY("灰色");

    companion object {
        fun fromKey(key: String?): PetColor? = entries.firstOrNull { it.name == key }

        /** The first colour nobody in [taken] has; when all are in use, start over from the first. */
        fun nextFree(taken: Collection<PetColor?>): PetColor = entries.firstOrNull { it !in taken } ?: entries[taken.size % entries.size]

        /**
         * The colour each pet is shown with: its own choice, otherwise the next free one in creation order
         * (pets saved before colours existed get a stable colour this way).
         */
        fun effective(pets: List<Pet>): Map<String, PetColor> {
            val result = LinkedHashMap<String, PetColor>()
            val taken = mutableListOf<PetColor?>()
            pets.sortedBy { it.createdAt }.forEach { p ->
                val own = fromKey(p.color)
                if (own != null) { result[p.id] = own; taken += own }
            }
            pets.sortedBy { it.createdAt }.filter { it.id !in result }.forEach { p ->
                val c = nextFree(taken)
                result[p.id] = c; taken += c
            }
            return result
        }
    }
}
