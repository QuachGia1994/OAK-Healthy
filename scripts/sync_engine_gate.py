#!/usr/bin/env python3
"""Fail-closed structural validation for P9.3 Sync Engine 2.0."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class GateError(RuntimeError):
    pass


def _read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise GateError(f"missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def _require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise GateError(f"{label}: missing {needle}")


def _reject(text: str, needles: tuple[str, ...], label: str) -> None:
    for needle in needles:
        if needle in text:
            raise GateError(f"{label}: must not persist payload field {needle}")


def validate() -> None:
    android_engine = _read("Android/app/src/main/java/com/example/supplementtracker/service/CloudSyncEngine.kt")
    android_queue = _read("Android/app/src/main/java/com/example/supplementtracker/service/SyncMutationQueue.kt")
    android_ops = _read("Android/app/src/main/java/com/example/supplementtracker/service/SyncOperationJournal.kt")
    android_test = _read("Android/app/src/test/java/com/example/supplementtracker/service/SyncOperationPolicyTest.kt")
    ios_coord = _read("iOS/Services/CloudSyncCoordinator.swift")
    ios_queue = _read("iOS/Services/SyncOperationJournal.swift")
    ios_test = _read("iOS/Tests/SyncOperationPolicyTests.swift")

    _require(android_engine, "maxOf(it.updatedAtEpochMs, it.deletedAtEpochMs ?: 0L)", "Android stale-delete guard")
    _require(android_engine, "SyncConflictPolicy.remoteMayApply(localTs, remoteTs)", "Android stale-delete guard")
    _require(android_queue, "entry.enqueuedAtEpochMs <= syncStartedEpochMs", "Android race-safe queue clear")
    _require(ios_queue, "$0.enqueuedAtEpochMs > syncStartedEpochMs", "iOS race-safe queue clear")
    _require(android_engine, "syncTwoWay(binId: String, force: Boolean = false)", "Android manual/auto backoff split")
    _require(android_engine, "SyncBackoffPolicy.canAttempt", "Android backoff")
    _require(ios_coord, "SyncBackoffPolicy.canAttempt", "iOS backoff")
    _require(ios_coord, "previewAndMergeConflict", "iOS conflict preview")
    _require(android_engine, "previewRemoteConflict", "Android conflict preview")
    _require(android_test, "staleRemoteDeletionCannotEraseNewerLocalEdit", "Android local-first test")
    _require(ios_test, "testStaleRemoteDeletionCannotEraseNewerLocalEdit", "iOS local-first test")

    forbidden = ("dailyDose", "supplementName", "IntakeRecord", "UserSupplement", "OAKBackup")
    _reject(android_queue + android_ops, forbidden, "Android queue/journal")
    _reject(ios_queue, forbidden, "iOS queue/journal")


def main() -> int:
    try:
        validate()
    except GateError as error:
        print(f"Sync engine gate failed: {error}")
        return 1
    print(
        "Sync engine gate passed: durable dirty-part queues, deterministic local-first conflicts, "
        "observable backoff/journal state, and race-safe queue clearing."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
