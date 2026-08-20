#!/usr/bin/env python3
"""Fail-closed repository contracts for health-data ownership and date integrity."""
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

FILES = {
    "android_status": ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/model/IntakeStatus.kt",
    "android_history_model": ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/model/IntakeRecord.kt",
    "android_policy": ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/util/DoseTimingPolicy.kt",
    "android_day": ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/util/HealthDayBoundary.kt",
    "android_client_name": ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/util/ClientNamePolicy.kt",
    "android_dao": ROOT / "Android/app/src/main/java/com/example/supplementtracker/data/local/SupplementDao.kt",
    "android_repo": ROOT / "Android/app/src/main/java/com/example/supplementtracker/data/repository/SupplementRepositoryImpl.kt",
    "android_cycle": ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/usecase/CalculateCycleUseCase.kt",
    "android_import": ROOT / "Android/app/src/main/java/com/example/supplementtracker/domain/usecase/ImportBackupUseCase.kt",
    "ios_policy": ROOT / "iOS/Services/DoseTimingPolicy.swift",
    "ios_cycle": ROOT / "iOS/Engine/CycleEngine.swift",
    "ios_day": ROOT / "iOS/Models/LocalDayCodec.swift",
    "ios_client_name": ROOT / "iOS/Models/ClientNamePolicy.swift",
    "ios_client_mutation": ROOT / "iOS/Services/ClientProfileMutationStore.swift",
    "ios_routine_mutation": ROOT / "iOS/Services/SupplementRoutineMutationStore.swift",
    "ios_history_mutation": ROOT / "iOS/Services/SupplementHistoryMutationStore.swift",
    "ios_recovery": ROOT / "iOS/Services/PendingImportRecoveryCoordinator.swift",
    "ios_export": ROOT / "iOS/Services/SupplementExport.swift",
    "flow_doc": ROOT / "docs/arch/HEALTH_DATA_FLOW.md",
}


def text(key: str) -> str:
    return FILES[key].read_text(encoding="utf-8")


def check_required_files() -> list[str]:
    return [f"missing {path.relative_to(ROOT)}" for path in FILES.values() if not path.exists()]


def check_android_contracts() -> list[str]:
    failures: list[str] = []
    status = text("android_status")
    policy = text("android_policy")
    day = text("android_day")
    dao = text("android_dao")
    repo = text("android_repo")
    history_model = text("android_history_model")
    cycle = text("android_cycle")
    import_flow = text("android_import")
    if 'TAKEN("Taken")' not in status or 'SKIPPED("Skipped")' not in status:
        failures.append("Android IntakeStatus must own persisted Taken/Skipped values")
    if "data class IntakeRecord" not in history_model or "not be used as event identity" not in history_model:
        failures.append("Android intake history must be a documented semantic domain model")
    for marker in ("SOON_WINDOW_MS", "MISSED_AFTER_MS", "completionRate", "isLateTaken"):
        if marker not in policy:
            failures.append(f"Android DoseTimingPolicy missing {marker}")
    if "endExclusive" not in day or "rangeFor" not in day:
        failures.append("Android HealthDayBoundary must expose half-open ranges")
    if "Normalizer.Form.NFD" not in text("android_client_name") or "Locale.ROOT" not in text("android_client_name"):
        failures.append("Android ClientNamePolicy must own locale-stable duplicate canonicalization")
    if "r.date < :endExclusive" not in dao or "date < :endExclusive" not in dao:
        failures.append("Android DAO health-day queries must use an exclusive upper bound")
    if "HealthDayBoundary.rangeFor(date)" not in repo:
        failures.append("Android removeIntake must use the requested date via HealthDayBoundary")
    if "LocalDate.now()" in repo[repo.find("override suspend fun removeIntake"):repo.find("override fun getRecordsByDateRange")]:
        failures.append("Android removeIntake must not substitute today's date")
    if "currentDate.isBefore(startDate)) return CycleStatus.OFF" not in cycle:
        failures.append("Android cycle engine must keep future-start routines OFF")
    for marker in ("validatedCycle(dto)", "IntakeStatus.fromStorage(record.status)"):
        if marker not in import_flow:
            failures.append(f"Android backup import missing semantic validation: {marker}")
    return failures


def check_ios_contracts() -> list[str]:
    failures: list[str] = []
    policy = text("ios_policy")
    day = text("ios_day")
    recovery = text("ios_recovery")
    export = text("ios_export")
    cycle = text("ios_cycle")
    for marker in ("soonWindowMilliseconds", "missedAfterMilliseconds", "completionRate", "isLateTaken"):
        if marker not in policy:
            failures.append(f"iOS DoseTimingPolicy missing {marker}")
    if "dateComponents([.year, .month, .day]" not in day or "TimeZone(secondsFromGMT: 0)" in day:
        failures.append("iOS LocalDayCodec must preserve calendar-day components without UTC conversion")
    if "diacriticInsensitive" not in text("ios_client_name") or "en_US_POSIX" not in text("ios_client_name"):
        failures.append("iOS ClientNamePolicy must own locale-stable duplicate canonicalization")
    for key in ("ios_client_mutation", "ios_routine_mutation", "ios_history_mutation"):
        content = text(key)
        if "try context.save()" not in content or "context.rollback()" not in content:
            failures.append(f"{FILES[key].name} must explicitly save and rollback persistence mutations")
    if "clientId: String" not in recovery or "UUID(uuidString: trimmedId)" not in recovery:
        failures.append("Pending import recovery must target stable client UUIDs")
    if "try? modelContext.save()" in recovery:
        failures.append("Pending import recovery must not swallow rollback save failures")
    if "static func importFile(" in export or "findSupplement(\n        named name" in export:
        failures.append("Legacy import must not retain the name-based merge path")
    if "guard currentDay >= startDay else { return .off }" not in cycle:
        failures.append("iOS cycle engine must keep future-start routines OFF")
    for marker in ("validateSemanticPayload", "IntakeStatus(rawValue: record.status)"):
        if marker not in export:
            failures.append(f"iOS backup import missing semantic validation: {marker}")
    return failures


def check_android_presentation_persistence_boundary() -> list[str]:
    failures: list[str] = []
    presentation = ROOT / "Android/app/src/main/java/com/example/supplementtracker/presentation"
    repository_decl = re.compile(r"\b(\w+)\s*:\s*(?:[\w.]+\.)?SupplementRepository\b")
    mutation_methods = (
        "saveSupplement", "updateSupplement", "deleteSupplement",
        "insertIntakeRecord", "removeIntake", "importBackupAtomic",
        "saveClient", "updateClient", "deleteClient",
    )
    for path in presentation.rglob("*.kt"):
        content = path.read_text(encoding="utf-8")
        receivers = set(repository_decl.findall(content))
        for receiver in receivers:
            for method in mutation_methods:
                marker = f"{receiver}.{method}("
                if marker in content:
                    failures.append(f"Android presentation owns persistence mutation: {path.name} contains {marker}")
    return failures


def check_ios_view_persistence_boundary() -> list[str]:
    failures: list[str] = []
    views = ROOT / "iOS/Views"
    direct_context_mutation = re.compile(r"\b\w*context\.(?:insert|delete|save)\s*\(", re.IGNORECASE)
    for path in views.glob("*.swift"):
        content = path.read_text(encoding="utf-8")
        if direct_context_mutation.search(content):
            failures.append(f"iOS View owns direct ModelContext persistence mutation: {path.name}")
    return failures


def check_docs() -> list[str]:
    doc = text("flow_doc")
    required = (
        "Supplement routine",
        "Intake history event",
        "DoseTimingPolicy",
        "HealthDayBoundary",
        "LocalDayCodec",
        "canonical dose",
        "rollback",
        "Migration strategy",
    )
    return [f"health-data flow doc missing {marker}" for marker in required if marker not in doc]


def run_gate() -> list[str]:
    failures = check_required_files()
    if failures:
        return failures
    failures.extend(check_android_contracts())
    failures.extend(check_ios_contracts())
    failures.extend(check_android_presentation_persistence_boundary())
    failures.extend(check_ios_view_persistence_boundary())
    failures.extend(check_docs())
    return failures


def main() -> int:
    failures = run_gate()
    if failures:
        print("Health-data integrity gate failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("Health-data integrity gate passed: ownership, canonical status/formulas, local-day boundaries and recovery identity are explicit.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
