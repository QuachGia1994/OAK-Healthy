package com.example.supplementtracker.domain.export

import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

object OAKBackupIntegrity {
    const val SCHEMA_VERSION = 1
    const val ALGORITHM = "sha256"

    fun create(data: OAKBackupDataDTO): OAKBackupIntegrityDTO {
        return OAKBackupIntegrityDTO(
            schemaVersion = SCHEMA_VERSION,
            algorithm = ALGORITHM,
            digest = digest(data),
            supplementCount = data.stack.size,
            historyCount = data.history.size
        )
    }

    fun validate(data: OAKBackupDataDTO, integrity: OAKBackupIntegrityDTO): Result<Unit> = runCatching {
        require(integrity.schemaVersion == SCHEMA_VERSION) { "Unsupported integrity schema" }
        require(integrity.algorithm.equals(ALGORITHM, ignoreCase = true)) { "Unsupported integrity algorithm" }
        require(integrity.supplementCount == data.stack.size) { "Supplement count mismatch" }
        require(integrity.historyCount == data.history.size) { "History count mismatch" }
        require(integrity.digest.equals(digest(data), ignoreCase = true)) { "Backup integrity mismatch" }
    }

    fun digest(data: OAKBackupDataDTO): String {
        val bytes = canonicalText(data).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    internal fun canonicalText(data: OAKBackupDataDTO): String {
        val lines = mutableListOf("version|${encoded(data.version)}", metaLine(data.meta))
        lines += data.stack.map(::supplementLine).sorted()
        lines += data.history.map(::historyLine).sorted()
        return lines.joinToString("\n")
    }

    private fun metaLine(meta: OAKBackupMetaDTO?): String {
        if (meta == null) return "meta|-"
        return listOf(
            "meta",
            meta.schemaVersion.toString(),
            meta.updatedAtEpochMs.toString(),
            encoded(meta.deviceId)
        ).joinToString("|")
    }

    private fun supplementLine(dto: OAKBackupSupplementDTO): String {
        val cycle = dto.cycle
        return listOf(
            "stack", encoded(dto.id), encoded(dto.name), encoded(dto.dailyDose), encoded(dto.intakeTime),
            encoded(dto.startDate), cycle.isContinuous.toString(), cycle.daysOn.toString(), cycle.daysOff.toString(),
            optional(cycle.durationMonths), optional(cycle.weeklyWeekdaysMask), optional(cycle.weeklyIntervalWeeks),
            encoded(cycle.weeklyAnchorDate.orEmpty()), optional(cycle.intervalDays), encoded(dto.lastTakenLocalDate.orEmpty()),
            dto.updatedAtEpochMs.toString(), optional(dto.deletedAtEpochMs), encoded(dto.modifiedFields.orEmpty().sorted().joinToString(","))
        ).joinToString("|")
    }

    private fun historyLine(dto: OAKBackupHistoryDTO): String = listOf(
        "history",
        encoded(dto.id),
        encoded(dto.supplementId),
        dto.dateEpochMs.toString(),
        encoded(dto.status),
        dto.updatedAtEpochMs.toString()
    ).joinToString("|")

    private fun optional(value: Any?): String = value?.toString().orEmpty()

    private fun encoded(value: String): String {
        return Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    }
}
