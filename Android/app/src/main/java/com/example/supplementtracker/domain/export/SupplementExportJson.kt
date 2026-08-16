package com.example.supplementtracker.domain.export

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

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
        dto.lastTakenLocalDate?.let { obj.put("lastTakenLocalDate", it) }
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
            cycle = decodeCycle(cycleObj),
            lastTakenLocalDate = obj.optString("lastTakenLocalDate", "").ifBlank { null }
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
        obj.put("intervalDays", dto.intervalDays)
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
            weeklyAnchorDate = obj.optString("weeklyAnchorDate", "").ifBlank { null },
            intervalDays = obj.optInt("intervalDays", -1).takeIf { it >= 2 }
        )
    }
}

data class OAKBackupPreview(
    val version: String,
    val supplementCount: Int,
    val historyCount: Int,
    val integrityVerified: Boolean
)

object OAKBackupJson {
    private const val HISTORY_COMPRESS_THRESHOLD = 200

    private fun stableHistoryId(supplementId: String, dateEpochMs: Long): String {
        return com.example.supplementtracker.domain.util.DoseEventKey.make(
            supplementId = supplementId,
            scheduledAtEpochMs = dateEpochMs
        )
    }

    private fun stableLegacySupplementId(dto: SupplementExportSupplementDTO): String =
        stableLegacySupplementId(
            name = dto.name,
            dailyDose = dto.dailyDose,
            intakeTime = dto.intakeTime,
            startDate = dto.startDate,
            cycle = dto.cycle
        )

    private fun stableLegacySupplementId(
        name: String,
        dailyDose: String,
        intakeTime: String,
        startDate: String,
        cycle: SupplementExportCycleDTO
    ): String {
        val key = listOf(
            "supplement", name.trim(), dailyDose.trim(), intakeTime.trim(), startDate.trim(),
            cycle.isContinuous.toString(), cycle.daysOn.toString(), cycle.daysOff.toString(),
            cycle.durationMonths?.toString().orEmpty(), cycle.weeklyWeekdaysMask?.toString().orEmpty(),
            cycle.weeklyIntervalWeeks?.toString().orEmpty(), cycle.intervalDays?.toString().orEmpty(),
            cycle.weeklyAnchorDate?.trim().orEmpty()
        ).joinToString("|").lowercase()
        return com.example.supplementtracker.domain.util.StableId.uuidFromString(key).toString()
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
        root.put("integrity", encodeIntegrity(OAKBackupIntegrity.create(data)))

        return root.toString(2)
    }

    fun decodeCompat(json: String): Result<OAKBackupDataDTO> {
        return runCatching { decodeCompatOrThrow(json) }
    }

    fun preview(json: String): Result<OAKBackupPreview> = decodeCompat(json).map { data ->
        OAKBackupPreview(
            version = data.version,
            supplementCount = data.stack.size,
            historyCount = data.history.size,
            integrityVerified = data.integrity != null
        )
    }

    private fun decodeCompatOrThrow(json: String): OAKBackupDataDTO {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) return decodeFromLegacyArray(trimmed)
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return decodeFromSupplementExport(json)
        return decodeFromRoot(root, json)
    }

    private fun decodeFromLegacyArray(trimmed: String): OAKBackupDataDTO {
        val array = JSONArray(trimmed)
        return OAKBackupDataDTO(
            version = OAKBackupSchema.VERSION,
            stack = decodeStackArray(array),
            history = emptyList()
        )
    }

    private fun decodeFromSupplementExport(json: String): OAKBackupDataDTO {
        val legacy = SupplementExportJson.decode(json).getOrThrow()
        return OAKBackupDataDTO(
            version = OAKBackupSchema.VERSION,
            stack = legacyStackFromExport(legacy),
            history = emptyList()
        )
    }

    private fun legacyStackFromExport(file: SupplementExportFileDTO): List<OAKBackupSupplementDTO> {
        return file.supplements.map { legacyDto ->
            OAKBackupSupplementDTO(
                id = stableLegacySupplementId(legacyDto),
                name = legacyDto.name,
                dailyDose = legacyDto.dailyDose,
                intakeTime = legacyDto.intakeTime,
                startDate = legacyDto.startDate,
                cycle = legacyDto.cycle,
                lastTakenLocalDate = legacyDto.lastTakenLocalDate
            )
        }
    }

    private fun decodeFromRoot(root: JSONObject, rawJson: String): OAKBackupDataDTO {
        val stackArray = root.optJSONArray("supplements") ?: root.optJSONArray("stack")
        if (stackArray == null) {
            require(!root.has("integrity")) { "Integrity manifest requires an OAK backup payload" }
            return decodeFromSupplementExport(rawJson)
        }
        val stack = decodeStackArray(stackArray)
        val (history, historyZlibBase64) = decodeHistoryCompat(root)
        val data = OAKBackupDataDTO(
            version = root.optString("version", OAKBackupSchema.VERSION),
            meta = decodeMeta(root),
            stack = stack,
            history = history,
            historyZlibBase64 = historyZlibBase64
        )
        val integrity = decodeIntegrity(root) ?: return data
        OAKBackupIntegrity.validate(data, integrity).getOrThrow()
        return data.copy(integrity = integrity)
    }

    private fun encodeIntegrity(integrity: OAKBackupIntegrityDTO): JSONObject {
        return JSONObject()
            .put("schemaVersion", integrity.schemaVersion)
            .put("algorithm", integrity.algorithm)
            .put("digest", integrity.digest)
            .put("supplementCount", integrity.supplementCount)
            .put("historyCount", integrity.historyCount)
    }

    private fun decodeIntegrity(root: JSONObject): OAKBackupIntegrityDTO? {
        if (!root.has("integrity")) return null
        require(!root.isNull("integrity")) { "Integrity manifest is null" }
        val obj = root.optJSONObject("integrity") ?: error("Invalid integrity manifest")
        return OAKBackupIntegrityDTO(
            schemaVersion = obj.optInt("schemaVersion", -1),
            algorithm = obj.optString("algorithm", ""),
            digest = obj.optString("digest", ""),
            supplementCount = obj.optInt("supplementCount", -1),
            historyCount = obj.optInt("historyCount", -1)
        )
    }

    private fun decodeMeta(root: JSONObject): OAKBackupMetaDTO? {
        val metaObj = root.optJSONObject("meta") ?: return null
        val deviceId = metaObj.optString("deviceId", "").trim()
        if (deviceId.isEmpty()) return null
        return OAKBackupMetaDTO(
            schemaVersion = metaObj.optInt("schemaVersion", 0),
            updatedAtEpochMs = metaObj.optLong("updatedAtEpochMs", 0L),
            deviceId = deviceId
        )
    }

    private fun decodeHistoryCompat(root: JSONObject): Pair<List<OAKBackupHistoryDTO>, String?> {
        val historyArray = root.optJSONArray("historyLogs") ?: root.optJSONArray("history") ?: JSONArray()
        val history = buildList {
            for (i in 0 until historyArray.length()) add(decodeHistory(historyArray.getJSONObject(i)))
        }.toMutableList()
        val zlib = root.optString("historyZlibBase64", "").trim().ifBlank { null }
        if (zlib != null) history.addAll(inflateHistoryZlib(zlib))
        val deduped = history.groupBy { it.id.lowercase() }.mapNotNull { (_, list) ->
            list.maxByOrNull { it.updatedAtEpochMs }
        }
        return deduped to zlib
    }

    private fun inflateHistoryZlib(historyZlibBase64: String): List<OAKBackupHistoryDTO> {
        val inflated = inflateZlibBase64Array(historyZlibBase64)
        return buildList {
            for (i in 0 until inflated.length()) {
                val obj = inflated.optJSONObject(i) ?: continue
                add(decodeHistory(obj))
            }
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
        dto.lastTakenLocalDate?.let { obj.put("lastTakenLocalDate", it) }
        obj.put("updatedAtEpochMs", dto.updatedAtEpochMs)
        dto.deletedAtEpochMs?.let { obj.put("deletedAtEpochMs", it) }
        dto.modifiedFields?.let { fields ->
            val arr = org.json.JSONArray()
            fields.forEach { arr.put(it) }
            obj.put("modifiedFields", arr)
        }
        return obj
    }

    private fun decodeSupplement(obj: JSONObject): OAKBackupSupplementDTO {
        val name = obj.optString("name", "")
        val dailyDose = obj.optString("dailyDose", "")
        val startDate = obj.optString("startDate", "1970-01-01")
        val intakeTime = obj.optString("intakeTime", "08:00")
        val cycle = decodeCycle(obj.optJSONObject("cycle") ?: JSONObject())
        val modifiedFieldsArray = obj.optJSONArray("modifiedFields")
        val modifiedFields = if (modifiedFieldsArray != null) {
            (0 until modifiedFieldsArray.length()).mapNotNull { modifiedFieldsArray.optString(it) }.filter { it.isNotBlank() }.toSet()
        } else null
        return OAKBackupSupplementDTO(
            id = obj.optString("id", "").ifBlank {
                stableLegacySupplementId(name, dailyDose, intakeTime, startDate, cycle)
            },
            name = name,
            dailyDose = dailyDose,
            intakeTime = intakeTime,
            startDate = startDate,
            cycle = cycle,
            lastTakenLocalDate = obj.optString("lastTakenLocalDate", "").ifBlank { null },
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
            deletedAtEpochMs = obj.optLong("deletedAtEpochMs", -1L).takeIf { it >= 0L },
            modifiedFields = modifiedFields
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
        obj.put("intervalDays", dto.intervalDays)
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
            weeklyAnchorDate = obj.optString("weeklyAnchorDate", "").ifBlank { null },
            intervalDays = obj.optInt("intervalDays", -1).takeIf { it >= 2 }
        )
    }
}
