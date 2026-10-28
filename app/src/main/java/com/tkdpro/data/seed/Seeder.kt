package com.tkdpro.data.seed

import com.tkdpro.core.database.dao.TechniqueDao
import com.tkdpro.core.database.entity.TechniqueCategory
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.di.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts the built-in technical library on first launch (idempotent, keyed by builtInKey).
 * This is reference content, not performance data: no records or statistics are seeded.
 */
@Singleton
class Seeder @Inject constructor(
    private val techniqueDao: TechniqueDao,
    private val clock: Clock,
) {
    suspend fun run() {
        val existing = techniqueDao.builtInKeys().toSet()
        val now = clock.now()
        val missing = BUILT_IN_TECHNIQUES.filter { it.key !in existing }.map { t ->
            TrainingTechnique(
                name = t.name,
                builtInKey = t.key,
                category = t.category,
                difficulty = t.difficulty,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (missing.isNotEmpty()) techniqueDao.insertAll(missing)
        // Scoring presets intentionally start empty: the athlete enters values from the current official rules.
    }

    data class BuiltIn(val key: String, val name: String, val category: TechniqueCategory, val difficulty: Int)

    companion object {
        val BUILT_IN_TECHNIQUES = listOf(
            BuiltIn("AP_CHAGI", "Ap Chagi", TechniqueCategory.KICK, 1),
            BuiltIn("DOLLYO_CHAGI", "Dollyo Chagi", TechniqueCategory.KICK, 2),
            BuiltIn("BANDAL_CHAGI", "Bandal Chagi", TechniqueCategory.KICK, 2),
            BuiltIn("YOP_CHAGI", "Yop Chagi", TechniqueCategory.KICK, 2),
            BuiltIn("MIREO_CHAGI", "Mireo Chagi", TechniqueCategory.KICK, 2),
            BuiltIn("NAERYO_CHAGI", "Naeryo Chagi", TechniqueCategory.KICK, 3),
            BuiltIn("HURYEO_CHAGI", "Huryeo Chagi", TechniqueCategory.KICK, 3),
            BuiltIn("DWI_CHAGI", "Dwi Chagi", TechniqueCategory.KICK, 3),
            BuiltIn("DWI_HURYEO_CHAGI", "Dwi Huryeo Chagi", TechniqueCategory.KICK, 4),
            BuiltIn("TWIO_DOLLYO_CHAGI", "Twio Dollyo Chagi", TechniqueCategory.KICK, 4),
            BuiltIn("MOMTONG_JIREUGI", "Momtong Jireugi", TechniqueCategory.PUNCH, 1),
            BuiltIn("BAL_BAKKUGI", "Bal Bakkugi", TechniqueCategory.FOOTWORK, 1),
            BuiltIn("STEP_IN", "Step In", TechniqueCategory.FOOTWORK, 1),
            BuiltIn("STEP_BACK", "Step Back", TechniqueCategory.FOOTWORK, 1),
            BuiltIn("MOMTONG_MAKGI", "Momtong Makgi", TechniqueCategory.BLOCK, 1),
        )
    }
}
