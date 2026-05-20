package com.example.supplementtracker.domain.export

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object SupplementExportJson {
    private fun stableSupplementId(name: String, startDate: String, intakeTime: String): String {
        return "s-" + com.example.supplementtracker.domain.util.StableId.hexSha256(
            listOf(name.trim(), startDate.trim(), intakeTime.trim()).joinToString("|")
        ).take(32)
    }

    private fun stableHistoryId(supplementId: String, dateEpochMs: Long): String {
        return com.example.supplementtracker.domain.util.DoseEventKey.make(supplementId = supplementId, scheduledAtEpochMs = dateEpochMs)
    }

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
        obj.put("weeklyWeekdaysMask", dto.weeklyWeekdaysMask)
        obj.put("weeklyIntervalWeeks", dto.weeklyIntervalWeeks)
        obj.put("weeklyAnchorDate", dto.weeklyAnchorDate)
        return obj
    }

    private fun decodeCycle(obj: JSONObject): SupplementExportCycleDTO {
        return SupplementExportCycleDTO(
            isContinuous = obj.optBoolean("isContinuous", false),
            daysOn = obj.optInt("daysOn", 1),
            daysOff = obj.optInt("daysOff", 0),
            durationMonths = obj.optInt("durationMonths", -1).takeIf { it >= 0 },
            weeklyWeekdaysMask = obj.optInt("weeklyWeekdaysMask", -1).takeIf { it >= 0 },
            weeklyIntervalWeeks = obj.optInt("weeklyIntervalWeeks", -1).takeIf { it >= 1 },
            weeklyAnchorDate = obj.optString("weeklyAnchorDate", "").ifBlank { null }
        )
    }
}

object OAKBackupJson {
    private const val HISTORY_COMPRESS_THRESHOLD = 200

    private fun stableSupplementId(name: String, startDate: String, intakeTime: String): String {
        return "s-" + com.example.supplementtracker.domain.util.StableId.hexSha256(
            listOf(name.trim(), startDate.trim(), intakeTime.trim()).joinToString("|")
        ).take(32)
    }

    private fun stableHistoryId(supplementId: String, dateEpochMs: Long): String {
        return com.example.supplementtracker.domain.util.DoseEventKey.make(
            supplementId = supplementId,
            scheduledAtEpochMs = dateEpochMs
        )
    }

    private fun stableLegacySupplementId(dto: SupplementExportSupplementDTO): String {
        val key = listOf(
            dto.name.trim(),
            dto.dailyDose.trim(),
            dto.intakeTime.trim(),
            dto.startDate.trim(),
            dto.cycle.isContinuous.toString(),
            dto.cycle.daysOn.toString(),
            dto.cycle.daysOff.toString(),
            dto.cycle.durationMonths?.toString().orEmpty(),
            dto.cycle.weeklyWeekdaysMask?.toString().orEmpty(),
            dto.cycle.weeklyIntervalWeeks?.toString().orEmpty(),
            dto.cycle.weeklyAnchorDate?.trim().orEmpty()
        ).joinToString("|")
        return "s-" + com.example.supplementtracker.domain.util.StableId.hexSha256(key).take(32)
    }

    fun encode(data: OAKBackupDataDTO): String {
        val root = JSONObject()
        root.put("version", data.version)
        data.meta?.let { meta ->
            val metaObj = JSONObject()
            metaObj.put("schemaVersion", meta.schemaVersion)
            metaObj.put("updatedAtEpochMs", meta.updatedAtEpochMs)
            metaObj.put("deviceId", meta.deviceId)
            root.put("meta", metaObj)
        }
        val supplementsArray = JSONArray()
        data.stack.forEach { dto ->
            supplementsArray.put(encodeSupplement(dto))
        }
        root.put("supplements", supplementsArray)

        val (historyLogsArray, historyZlibBase64) = encodeHistoryPayload(data.history)
        root.put("historyLogs", historyLogsArray)
        if (!historyZlibBase64.isNullOrBlank()) root.put("historyZlibBase64", historyZlibBase64)

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
                            id = stableLegacySupplementId(legacyDto),
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
                            id = stableLegacySupplementId(legacyDto),
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
            }.toMutableList()
            
            val historyZlibBase64 = root.optString("historyZlibBase64", "").trim().ifBlank { null }
            if (historyZlibBase64 != null) {
                val inflated = inflateZlibBase64Array(historyZlibBase64)
                for (i in 0 until inflated.length()) {
                    val obj = inflated.optJSONObject(i) ?: continue
                    history.add(decodeHistory(obj))
                }
            }
            
            val dedupedHistory = history
                .groupBy { it.id.lowercase() }
                .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAtEpochMs } }
            
            val metaObj = root.optJSONObject("meta")
            val meta = metaObj?.let {
                val deviceId = it.optString("deviceId", "").trim()
                if (deviceId.isEmpty()) return@let null
                OAKBackupMetaDTO(
                    schemaVersion = it.optInt("schemaVersion", 0),
                    updatedAtEpochMs = it.optLong("updatedAtEpochMs", 0L),
                    deviceId = deviceId
                )
            }

            OAKBackupDataDTO(
                version = root.optString("version", OAKBackupSchema.VERSION),
                meta = meta,
                stack = stack,
                history = dedupedHistory,
                historyZlibBase64 = historyZlibBase64
            )
        }
    }
    
    private fun encodeHistoryPayload(history: List<OAKBackupHistoryDTO>): Pair<JSONArray, String?> {
        if (history.size <= HISTORY_COMPRESS_THRESHOLD) {
            val array = JSONArray()
            history.forEach { array.put(encodeHistory(it)) }
            return array to null
        }
        val array = JSONArray()
        val full = JSONArray()
        history.forEach { dto -> full.put(encodeHistory(dto)) }
        val raw = full.toString()
        val z = zlib(raw)
        val b64 = Base64.encodeToString(z, Base64.NO_WRAP)
        return array to b64
    }
    
    private fun zlib(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        DeflaterOutputStream(baos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return baos.toByteArray()
    }
    
    private fun inflateZlibBase64Array(base64: String): JSONArray {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val text = InflaterInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JSONArray(text)
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
        obj.put("updatedAtEpochMs", dto.updatedAtEpochMs)
        dto.deletedAtEpochMs?.let { obj.put("deletedAtEpochMs", it) }
        return obj
    }

    private fun decodeSupplement(obj: JSONObject): OAKBackupSupplementDTO {
        val name = obj.optString("name", "")
        val startDate = obj.optString("startDate", "1970-01-01")
        val intakeTime = obj.optString("intakeTime", "08:00")
        return OAKBackupSupplementDTO(
            id = obj.optString("id", "").ifBlank { stableSupplementId(name, startDate, intakeTime) },
            name = name,
            dailyDose = obj.optString("dailyDose", ""),
            intakeTime = intakeTime,
            startDate = startDate,
            cycle = decodeCycle(obj.optJSONObject("cycle") ?: JSONObject()),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
            deletedAtEpochMs = obj.optLong("deletedAtEpochMs", -1L).takeIf { it >= 0L }
        )
    }

    private fun encodeHistory(dto: OAKBackupHistoryDTO): JSONObject {
        val obj = JSONObject()
        obj.put("id", dto.id)
        obj.put("supplementId", dto.supplementId)
        obj.put("dateEpochMs", dto.dateEpochMs)
        obj.put("status", dto.status)
        obj.put("updatedAtEpochMs", dto.updatedAtEpochMs)
        return obj
    }

    private fun decodeHistory(obj: JSONObject): OAKBackupHistoryDTO {
        val supplementId = obj.optString("supplementId", "")
        if (supplementId.isBlank()) error("Missing supplementId")
        val dateEpochMs = obj.optLong("dateEpochMs", 0L)
        return OAKBackupHistoryDTO(
            id = obj.optString("id", "").ifBlank { stableHistoryId(supplementId, dateEpochMs) },
            supplementId = supplementId,
            dateEpochMs = dateEpochMs,
            status = obj.optString("status", "Taken"),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L)
        )
    }

    private fun encodeCycle(dto: SupplementExportCycleDTO): JSONObject {
        val obj = JSONObject()
        obj.put("isContinuous", dto.isContinuous)
        obj.put("daysOn", dto.daysOn)
        obj.put("daysOff", dto.daysOff)
        obj.put("durationMonths", dto.durationMonths)
        obj.put("weeklyWeekdaysMask", dto.weeklyWeekdaysMask)
        obj.put("weeklyIntervalWeeks", dto.weeklyIntervalWeeks)
        obj.put("weeklyAnchorDate", dto.weeklyAnchorDate)
        return obj
    }

    private fun decodeCycle(obj: JSONObject): SupplementExportCycleDTO {
        return SupplementExportCycleDTO(
            isContinuous = obj.optBoolean("isContinuous", false),
            daysOn = obj.optInt("daysOn", 1),
            daysOff = obj.optInt("daysOff", 0),
            durationMonths = obj.optInt("durationMonths", -1).takeIf { it >= 0 },
            weeklyWeekdaysMask = obj.optInt("weeklyWeekdaysMask", -1).takeIf { it >= 0 },
            weeklyIntervalWeeks = obj.optInt("weeklyIntervalWeeks", -1).takeIf { it >= 1 },
            weeklyAnchorDate = obj.optString("weeklyAnchorDate", "").ifBlank { null }
        )
    }
}
