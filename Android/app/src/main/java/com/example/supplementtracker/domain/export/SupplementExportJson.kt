package com.example.supplementtracker.domain.export

import org.json.JSONArray
import org.json.JSONObject

object SupplementExportJson {
    fun encode(file: SupplementExportFileDTO): String {
        val root = JSONObject()
        root.put("schemaVersion", file.schemaVersion)
        root.put("exportedAtEpochMs", file.exportedAtEpochMs)
        val supplements = JSONArray()
        file.supplements.forEach { dto ->
            supplements.put(encodeSupplement(dto))
        }
        root.put("supplements", supplements)
        return root.toString(2)
    }

    fun decode(json: String): Result<SupplementExportFileDTO> {
        return runCatching {
            val root = JSONObject(json)
            val schemaVersion = root.getInt("schemaVersion")
            if (schemaVersion != SupplementExportSchema.VERSION) error("Invalid schema")
            val exportedAtEpochMs = root.optLong("exportedAtEpochMs", 0L)
            val supplementsArray = root.optJSONArray("supplements") ?: JSONArray()
            val supplements = buildList {
                for (i in 0 until supplementsArray.length()) {
                    val obj = supplementsArray.getJSONObject(i)
                    add(decodeSupplement(obj))
                }
            }
            SupplementExportFileDTO(
                schemaVersion = schemaVersion,
                exportedAtEpochMs = exportedAtEpochMs,
                supplements = supplements
            )
        }
    }

    private fun encodeSupplement(dto: SupplementExportSupplementDTO): JSONObject {
        val obj = JSONObject()
        obj.put("name", dto.name)
        obj.put("dailyDose", dto.dailyDose)
        obj.put("intakeTime", dto.intakeTime)
        obj.put("startDate", dto.startDate)
        obj.put("category", dto.category)
        obj.put("cycle", encodeCycle(dto.cycle))
        return obj
    }

    private fun decodeSupplement(obj: JSONObject): SupplementExportSupplementDTO {
        val cycleObj = obj.getJSONObject("cycle")
        return SupplementExportSupplementDTO(
            name = obj.getString("name"),
            dailyDose = obj.optString("dailyDose", ""),
            intakeTime = obj.optString("intakeTime", "08:00"),
            startDate = obj.getString("startDate"),
            category = obj.optString("category", "").ifBlank { null },
            cycle = decodeCycle(cycleObj)
        )
    }

    private fun encodeCycle(dto: SupplementExportCycleDTO): JSONObject {
        val obj = JSONObject()
        obj.put("isContinuous", dto.isContinuous)
        obj.put("daysOn", dto.daysOn)
        obj.put("daysOff", dto.daysOff)
        obj.put("durationMonths", dto.durationMonths)
        return obj
    }

    private fun decodeCycle(obj: JSONObject): SupplementExportCycleDTO {
        return SupplementExportCycleDTO(
            isContinuous = obj.optBoolean("isContinuous", false),
            daysOn = obj.optInt("daysOn", 1),
            daysOff = obj.optInt("daysOff", 0),
            durationMonths = obj.optInt("durationMonths", -1).takeIf { it >= 0 }
        )
    }
}

object OAKBackupJson {
    fun encode(data: OAKBackupDataDTO): String {
        val root = JSONObject()
        root.put("version", data.version)
        val supplementsArray = JSONArray()
        data.stack.forEach { dto ->
            supplementsArray.put(encodeSupplement(dto))
        }
        root.put("supplements", supplementsArray)

        val historyLogsArray = JSONArray()
        data.history.forEach { dto ->
            historyLogsArray.put(encodeHistory(dto))
        }
        root.put("historyLogs", historyLogsArray)

        return root.toString(2)
    }

    fun decodeCompat(json: String): Result<OAKBackupDataDTO> {
        return runCatching {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                return@runCatching OAKBackupDataDTO(
                    version = OAKBackupSchema.VERSION,
                    stack = decodeStackArray(array),
                    history = emptyList()
                )
            }

            val root = runCatching { JSONObject(trimmed) }.getOrNull()
            if (root == null) {
                val legacy = SupplementExportJson.decode(json).getOrThrow()
                return@runCatching OAKBackupDataDTO(
                    version = OAKBackupSchema.VERSION,
                    stack = legacy.supplements.map { legacyDto ->
                        OAKBackupSupplementDTO(
                            id = java.util.UUID.randomUUID().toString(),
                            name = legacyDto.name,
                            dailyDose = legacyDto.dailyDose,
                            intakeTime = legacyDto.intakeTime,
                            startDate = legacyDto.startDate,
                            cycle = legacyDto.cycle
                        )
                    },
                    history = emptyList()
                )
            }

            val stackArray = root.optJSONArray("supplements") ?: root.optJSONArray("stack")
            if (stackArray == null) {
                val legacy = SupplementExportJson.decode(json).getOrThrow()
                return@runCatching OAKBackupDataDTO(
                    version = OAKBackupSchema.VERSION,
                    stack = legacy.supplements.map { legacyDto ->
                        OAKBackupSupplementDTO(
                            id = java.util.UUID.randomUUID().toString(),
                            name = legacyDto.name,
                            dailyDose = legacyDto.dailyDose,
                            intakeTime = legacyDto.intakeTime,
                            startDate = legacyDto.startDate,
                            cycle = legacyDto.cycle
                        )
                    },
                    history = emptyList()
                )
            }

            val stack = decodeStackArray(stackArray)

            val historyArray = root.optJSONArray("historyLogs") ?: root.optJSONArray("history") ?: JSONArray()
            val history = buildList {
                for (i in 0 until historyArray.length()) {
                    add(decodeHistory(historyArray.getJSONObject(i)))
                }
            }

            OAKBackupDataDTO(
                version = root.optString("version", OAKBackupSchema.VERSION),
                stack = stack,
                history = history
            )
        }
    }

    private fun decodeStackArray(array: JSONArray): List<OAKBackupSupplementDTO> {
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(decodeSupplement(obj))
            }
        }
    }

    private fun encodeSupplement(dto: OAKBackupSupplementDTO): JSONObject {
        val obj = JSONObject()
        obj.put("id", dto.id)
        obj.put("name", dto.name)
        obj.put("dailyDose", dto.dailyDose)
        obj.put("intakeTime", dto.intakeTime)
        obj.put("startDate", dto.startDate)
        obj.put("cycle", encodeCycle(dto.cycle))
        return obj
    }

    private fun decodeSupplement(obj: JSONObject): OAKBackupSupplementDTO {
        return OAKBackupSupplementDTO(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            dailyDose = obj.optString("dailyDose", ""),
            intakeTime = obj.optString("intakeTime", "08:00"),
            startDate = obj.optString("startDate", "1970-01-01"),
            cycle = decodeCycle(obj.optJSONObject("cycle") ?: JSONObject())
        )
    }

    private fun encodeHistory(dto: OAKBackupHistoryDTO): JSONObject {
        val obj = JSONObject()
        obj.put("id", dto.id)
        obj.put("supplementId", dto.supplementId)
        obj.put("dateEpochMs", dto.dateEpochMs)
        obj.put("status", dto.status)
        return obj
    }

    private fun decodeHistory(obj: JSONObject): OAKBackupHistoryDTO {
        return OAKBackupHistoryDTO(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            supplementId = obj.getString("supplementId"),
            dateEpochMs = obj.optLong("dateEpochMs", 0L),
            status = obj.optString("status", "Taken")
        )
    }

    private fun encodeCycle(dto: SupplementExportCycleDTO): JSONObject {
        val obj = JSONObject()
        obj.put("isContinuous", dto.isContinuous)
        obj.put("daysOn", dto.daysOn)
        obj.put("daysOff", dto.daysOff)
        obj.put("durationMonths", dto.durationMonths)
        return obj
    }

    private fun decodeCycle(obj: JSONObject): SupplementExportCycleDTO {
        return SupplementExportCycleDTO(
            isContinuous = obj.optBoolean("isContinuous", false),
            daysOn = obj.optInt("daysOn", 1),
            daysOff = obj.optInt("daysOff", 0),
            durationMonths = obj.optInt("durationMonths", -1).takeIf { it >= 0 }
        )
    }
}
