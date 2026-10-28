package com.tkdpro.core.ui

import androidx.annotation.StringRes
import com.tkdpro.R
import com.tkdpro.core.database.entity.AnalysisStatus
import com.tkdpro.core.database.entity.BodySide
import com.tkdpro.core.database.entity.GoalCategory
import com.tkdpro.core.database.entity.GoalStatus
import com.tkdpro.core.database.entity.MatchEventType
import com.tkdpro.core.database.entity.MatchResult
import com.tkdpro.core.database.entity.PerformanceTestType
import com.tkdpro.core.database.entity.ReactionTestType
import com.tkdpro.core.database.entity.RunType
import com.tkdpro.core.database.entity.TechniqueCategory
import com.tkdpro.core.database.entity.TrainingCategory
import com.tkdpro.core.datastore.DashboardCard
import com.tkdpro.core.datastore.ThemeMode
import com.tkdpro.core.datastore.UnitSystem
import com.tkdpro.core.domain.motion.QualityIssue
import com.tkdpro.core.domain.reaction.InvalidReason
import com.tkdpro.core.domain.run.PersonalRecord
import com.tkdpro.data.repository.TimelineType

@StringRes fun TrainingCategory.label(): Int = when (this) {
    TrainingCategory.TECHNICAL -> R.string.cat_technical
    TrainingCategory.TACTICAL -> R.string.cat_tactical
    TrainingCategory.SPARRING -> R.string.cat_sparring
    TrainingCategory.FOOTWORK -> R.string.cat_footwork
    TrainingCategory.REACTION -> R.string.cat_reaction
    TrainingCategory.COMBINATION -> R.string.cat_combination
    TrainingCategory.CONDITIONING -> R.string.cat_conditioning
    TrainingCategory.STRENGTH_POWER -> R.string.cat_strength
    TrainingCategory.MOBILITY -> R.string.cat_mobility
    TrainingCategory.RECOVERY -> R.string.cat_recovery
}

@StringRes fun TechniqueCategory.label(): Int = when (this) {
    TechniqueCategory.KICK -> R.string.tcat_kick
    TechniqueCategory.PUNCH -> R.string.tcat_punch
    TechniqueCategory.FOOTWORK -> R.string.tcat_footwork
    TechniqueCategory.BLOCK -> R.string.tcat_block
    TechniqueCategory.OTHER -> R.string.tcat_other
}

@StringRes fun BodySide.label(): Int = when (this) {
    BodySide.RIGHT -> R.string.side_right
    BodySide.LEFT -> R.string.side_left
    BodySide.BOTH -> R.string.side_both
}

@StringRes fun RunType.label(): Int = when (this) {
    RunType.EASY -> R.string.run_easy
    RunType.ENDURANCE -> R.string.run_endurance
    RunType.TEMPO -> R.string.run_tempo
    RunType.INTERVAL -> R.string.run_interval
    RunType.RECOVERY -> R.string.run_recovery
    RunType.SPRINT -> R.string.run_sprint
    RunType.CUSTOM -> R.string.run_custom
}

@StringRes fun MatchResult.label(): Int = when (this) {
    MatchResult.WIN -> R.string.result_win
    MatchResult.LOSS -> R.string.result_loss
    MatchResult.DRAW -> R.string.result_draw
    MatchResult.NOT_RECORDED -> R.string.result_not_recorded
}

@StringRes fun MatchEventType.label(): Int = when (this) {
    MatchEventType.ATTACK -> R.string.event_attack
    MatchEventType.POINT_SCORED -> R.string.event_point_scored
    MatchEventType.POINT_CONCEDED -> R.string.event_point_conceded
    MatchEventType.COUNTER -> R.string.event_counter
    MatchEventType.DEFENSE -> R.string.event_defense
    MatchEventType.PENALTY -> R.string.event_penalty
    MatchEventType.NOTE -> R.string.event_note
}

@StringRes fun ReactionTestType.label(): Int = when (this) {
    ReactionTestType.VISUAL -> R.string.reaction_visual
    ReactionTestType.AUDIO -> R.string.reaction_audio
    ReactionTestType.RANDOM -> R.string.reaction_random
    ReactionTestType.REPEATED -> R.string.reaction_repeated
}

@StringRes fun ReactionTestType.description(): Int = when (this) {
    ReactionTestType.VISUAL -> R.string.reaction_visual_desc
    ReactionTestType.AUDIO -> R.string.reaction_audio_desc
    ReactionTestType.RANDOM -> R.string.reaction_random_desc
    ReactionTestType.REPEATED -> R.string.reaction_repeated_desc
}

@StringRes fun PerformanceTestType.label(): Int = when (this) {
    PerformanceTestType.SINGLE_LEG_BALANCE -> R.string.ptest_balance
    PerformanceTestType.SINGLE_LEG_BALANCE_EYES_CLOSED -> R.string.ptest_balance_closed
    PerformanceTestType.TAPPING_COORDINATION -> R.string.ptest_tapping
    PerformanceTestType.SIT_AND_REACH -> R.string.ptest_sit_reach
    PerformanceTestType.FRONT_SPLIT -> R.string.ptest_front_split
    PerformanceTestType.SIDE_SPLIT -> R.string.ptest_side_split
    PerformanceTestType.STANDING_LONG_JUMP -> R.string.ptest_long_jump
    PerformanceTestType.VERTICAL_JUMP -> R.string.ptest_vertical_jump
    PerformanceTestType.HEXAGON_AGILITY -> R.string.ptest_hexagon
}

@StringRes fun PerformanceTestType.instructions(): Int = when (this) {
    PerformanceTestType.SINGLE_LEG_BALANCE -> R.string.ptest_balance_how
    PerformanceTestType.SINGLE_LEG_BALANCE_EYES_CLOSED -> R.string.ptest_balance_closed_how
    PerformanceTestType.TAPPING_COORDINATION -> R.string.ptest_tapping_how
    PerformanceTestType.SIT_AND_REACH -> R.string.ptest_sit_reach_how
    PerformanceTestType.FRONT_SPLIT -> R.string.ptest_split_how
    PerformanceTestType.SIDE_SPLIT -> R.string.ptest_split_how
    PerformanceTestType.STANDING_LONG_JUMP -> R.string.ptest_long_jump_how
    PerformanceTestType.VERTICAL_JUMP -> R.string.ptest_vertical_jump_how
    PerformanceTestType.HEXAGON_AGILITY -> R.string.ptest_hexagon_how
}

@StringRes fun GoalCategory.label(): Int = when (this) {
    GoalCategory.TECHNICAL -> R.string.goal_technical
    GoalCategory.COMPETITION -> R.string.goal_competition
    GoalCategory.RUNNING -> R.string.goal_running
    GoalCategory.FITNESS -> R.string.goal_fitness
    GoalCategory.BELT -> R.string.goal_belt
    GoalCategory.OTHER -> R.string.goal_other
}

@StringRes fun GoalStatus.label(): Int = when (this) {
    GoalStatus.ACTIVE -> R.string.goal_status_active
    GoalStatus.ACHIEVED -> R.string.goal_status_achieved
    GoalStatus.ARCHIVED -> R.string.goal_status_archived
}

@StringRes fun AnalysisStatus.label(): Int = when (this) {
    AnalysisStatus.COMPLETED -> R.string.analysis_completed
    AnalysisStatus.INSUFFICIENT_QUALITY -> R.string.analysis_insufficient
    AnalysisStatus.FAILED -> R.string.analysis_failed
}

@StringRes fun QualityIssue.label(): Int = when (this) {
    QualityIssue.TOO_SHORT -> R.string.qi_too_short
    QualityIssue.TOO_FEW_FRAMES -> R.string.qi_too_few_frames
    QualityIssue.LOW_FRAME_RATE -> R.string.qi_low_fps
    QualityIssue.LOW_RESOLUTION -> R.string.qi_low_resolution
    QualityIssue.POSE_RARELY_DETECTED -> R.string.qi_pose_rare
    QualityIssue.KEY_LANDMARKS_LOW_VISIBILITY -> R.string.qi_low_visibility
    QualityIssue.BODY_OUT_OF_FRAME -> R.string.qi_out_of_frame
}

@StringRes fun InvalidReason.label(): Int = when (this) {
    InvalidReason.FALSE_START -> R.string.invalid_false_start
    InvalidReason.ANTICIPATION -> R.string.invalid_anticipation
    InvalidReason.LAPSE -> R.string.invalid_lapse
    InvalidReason.NO_RESPONSE -> R.string.invalid_no_response
}

@StringRes fun PersonalRecord.Kind.label(): Int = when (this) {
    PersonalRecord.Kind.LONGEST_DISTANCE -> R.string.pr_longest_distance
    PersonalRecord.Kind.LONGEST_DURATION -> R.string.pr_longest_duration
    PersonalRecord.Kind.FASTEST_1K -> R.string.pr_fastest_1k
    PersonalRecord.Kind.FASTEST_5K -> R.string.pr_fastest_5k
    PersonalRecord.Kind.FASTEST_10K -> R.string.pr_fastest_10k
}

@StringRes fun DashboardCard.label(): Int = when (this) {
    DashboardCard.TODAY -> R.string.card_today
    DashboardCard.RECOVERY -> R.string.card_recovery
    DashboardCard.WEEKLY_VOLUME -> R.string.card_weekly_volume
    DashboardCard.RUNNING -> R.string.card_running
    DashboardCard.TECHNICAL -> R.string.card_technical
    DashboardCard.REACTION -> R.string.card_reaction
    DashboardCard.VIDEO -> R.string.card_video
    DashboardCard.COMPETITION -> R.string.card_competition
    DashboardCard.GOALS -> R.string.card_goals
    DashboardCard.AI_OBSERVATIONS -> R.string.card_ai
    DashboardCard.TIMELINE -> R.string.card_timeline
}

@StringRes fun TimelineType.label(): Int = when (this) {
    TimelineType.TRAINING -> R.string.tl_training
    TimelineType.RUN -> R.string.tl_run
    TimelineType.MOTION -> R.string.tl_motion
    TimelineType.MATCH -> R.string.tl_match
    TimelineType.REACTION -> R.string.tl_reaction
    TimelineType.RECOVERY -> R.string.tl_recovery
    TimelineType.ACHIEVEMENT -> R.string.tl_achievement
}

@StringRes fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes fun UnitSystem.label(): Int = when (this) {
    UnitSystem.METRIC -> R.string.units_metric
    UnitSystem.IMPERIAL -> R.string.units_imperial
}

/** English/Turkish explanation of a built-in technique, shown under its romanised Korean name. */
@StringRes fun builtInTechniqueDescription(key: String?): Int? = when (key) {
    "AP_CHAGI" -> R.string.tech_ap_chagi
    "DOLLYO_CHAGI" -> R.string.tech_dollyo_chagi
    "BANDAL_CHAGI" -> R.string.tech_bandal_chagi
    "YOP_CHAGI" -> R.string.tech_yop_chagi
    "MIREO_CHAGI" -> R.string.tech_mireo_chagi
    "NAERYO_CHAGI" -> R.string.tech_naeryo_chagi
    "HURYEO_CHAGI" -> R.string.tech_huryeo_chagi
    "DWI_CHAGI" -> R.string.tech_dwi_chagi
    "DWI_HURYEO_CHAGI" -> R.string.tech_dwi_huryeo_chagi
    "TWIO_DOLLYO_CHAGI" -> R.string.tech_twio_dollyo_chagi
    "MOMTONG_JIREUGI" -> R.string.tech_momtong_jireugi
    "BAL_BAKKUGI" -> R.string.tech_bal_bakkugi
    "STEP_IN" -> R.string.tech_step_in
    "STEP_BACK" -> R.string.tech_step_back
    "MOMTONG_MAKGI" -> R.string.tech_momtong_makgi
    else -> null
}
