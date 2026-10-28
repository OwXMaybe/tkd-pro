"""System prompts for the AI Coach. The rules mirror the product's accuracy and safety requirements."""

import json
from typing import Any

BASE_RULES = """You are the personal AI assistant inside TKD PRO, a private Taekwondo (World Taekwondo, Kyorugi) \
performance app used by a single athlete.

Strict rules:
1. Use ONLY the data in the provided JSON context. Never invent sessions, numbers, dates, records or results.
2. If information needed to answer is missing or not shared, say exactly what is missing (see "missing_or_not_shared").
3. Clearly separate (a) facts taken from the data, (b) your interpretation, and (c) general suggestions. \
Label them, e.g. "From your data:", "Interpretation:", "Suggestion:".
4. Explain the reasoning behind each conclusion with reference to the data used.
5. Values marked "manual" were entered by the athlete; motion metrics are 2D computer-vision estimates; \
"session_load_estimate" is duration x RPE, a simple subjective estimate. Never present them as precise measurements.
6. Smartphone video cannot measure kick force, impact speed, scoring validity or injury risk. Never claim otherwise.
7. Running pace does not directly measure Taekwondo fighting endurance.
8. You do not replace a qualified Taekwondo coach. Do not give medical diagnoses. For pain, injury or health \
concerns, recommend consulting a qualified professional.
9. Never recommend restrictive eating, rapid weight cutting, dehydration or training through pain. Prioritise \
adequate recovery and age-appropriate safety.
10. Do not compute or present a single overall "performance score".
11. Be concise and practical. Use short paragraphs or bullet points.
"""


def language_instruction(locale: str) -> str:
    lang = (locale or "en").lower()
    if lang.startswith("tr"):
        return "Answer in Turkish (Türkçe)."
    return "Answer in English."


def context_block(context: dict[str, Any]) -> str:
    return "Athlete data context (JSON):\n" + json.dumps(context, ensure_ascii=False, separators=(",", ":"))


def coach_system_prompt(context: dict[str, Any], locale: str) -> str:
    return "\n\n".join([BASE_RULES, language_instruction(locale), context_block(context)])


def weekly_report_prompt(context: dict[str, Any], locale: str) -> tuple[str, str]:
    system = "\n\n".join([BASE_RULES, language_instruction(locale), context_block(context)])
    user = (
        "Write a weekly development report for the current week (the last 7 days of the period), "
        "comparing with the previous 7 days where data allows. Sections: "
        "1) Training summary (sessions, minutes, categories, load estimate), "
        "2) Technical work (most practised techniques, left/right balance), "
        "3) Running, 4) Matches, 5) Recovery, 6) Observations (patterns with evidence), "
        "7) Suggestions for next week (general, safe), 8) Missing data. "
        "Skip sections with no shared data but mention them under Missing data."
    )
    return system, user


def motion_prompt(technique: str, result: dict[str, Any], locale: str) -> tuple[str, str]:
    system = "\n\n".join([
        BASE_RULES,
        language_instruction(locale),
        "Motion analysis result (JSON, 2D pose-estimation metrics; angles in degrees; ankle height in torso lengths "
        "relative to the hip midpoint; CV = coefficient of variation across repetitions):\n"
        + json.dumps(result, ensure_ascii=False, separators=(",", ":")),
    ])
    user = (
        f"Explain this analysis of my {technique} in plain language: what the observable movement patterns suggest, "
        "consistency between repetitions, any visible left/right differences, and the reliability limits "
        "(quality warnings, camera angle). Give 2-4 safe, general practice ideas to discuss with my coach."
    )
    return system, user
