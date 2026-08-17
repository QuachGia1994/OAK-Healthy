#!/usr/bin/env python3
"""Machine-checkable P10.2 performance and battery contracts."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def validate_repository() -> None:
    dao = read("Android/app/src/main/java/com/example/supplementtracker/data/local/SupplementDao.kt")
    engine = read("Android/app/src/main/java/com/example/supplementtracker/service/CloudSyncEngine.kt")
    history = read("Android/app/src/main/java/com/example/supplementtracker/presentation/home/HistoryViewModel.kt")
    coach = read("Android/app/src/main/java/com/example/supplementtracker/service/CoachWorkspaceSource.kt")
    android_budget = read("Android/app/src/main/java/com/example/supplementtracker/service/PerformanceBudgets.kt")
    ios_store = read("iOS/Services/ClientScopedStore.swift")
    ios_sync = read("iOS/Services/CloudSyncCoordinator.swift")
    ios_budget = read("iOS/Services/PerformanceBudgets.swift")

    require("SELECT EXISTS(" in dao, "Android dirty checks must use SQL EXISTS")
    require("repository.hasSupplementChangesSince" in engine, "Android stack dirty check must avoid snapshot materialization")
    require("repository.hasHistoryChangesSince" in engine, "Android history dirty check must avoid snapshot materialization")
    require("getRecordsByDateRange" in history and "observeAllRecordsByClient(id)" not in history,
            "Android History must use entitlement-bounded date queries")
    require("getRecordsByDateRange" in coach and "getAllRecordsByClient" not in coach,
            "Android Coach source must use a bounded date window")
    require("BACKGROUND_SYNC_MINUTES = 30L" in android_budget, "Android background sync budget must be 30 minutes")

    require("descriptor.fetchLimit = max(0, limit)" in ios_store, "iOS history fetches must have fetch limits")
    require("descriptor.fetchLimit = 1" in ios_store, "iOS dirty checks must use existence-sized fetches")
    require("flatMap(\\.intakeRecords)" not in ios_store, "iOS history hot path must not materialize all supplement relationships")
    require("realtimeActivePollSeconds = 30" in ios_budget, "iOS active fallback polling must be at least 30 seconds")
    require("PerformanceBudgets.realtimeActivePollSeconds" in ios_sync, "iOS sync must consume the polling budget")


def main() -> int:
    try:
        validate_repository()
    except (OSError, RuntimeError) as error:
        print(f"Performance/battery gate failed: {error}")
        return 1
    print("Performance/battery gate passed: bounded history queries, existence dirty checks, and reduced fallback wakeups.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
