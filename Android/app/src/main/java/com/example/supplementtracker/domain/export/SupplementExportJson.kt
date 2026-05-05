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
            category = obj.optString("category", null),
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

