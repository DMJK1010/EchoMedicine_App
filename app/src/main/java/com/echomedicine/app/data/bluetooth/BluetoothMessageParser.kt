package com.echomedicine.app.data.bluetooth

import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.Schedule

/**
 * Arduino_Master로부터 수신된 raw 문자열을 도메인 모델로 변환하는 파서.
 *
 * 메시지 분류 규칙:
 * - "💊" → MedicineAlert
 * - "✅" + "복용 완료!" → TakenConfirmation
 * - "✅" + "설정 완료" → SettingConfirmation
 * - "⚠️" → RealertWarning
 * - "📅" → DailyReset
 * - "📋" → ScheduleInfo
 * - 그 외 → Unknown
 */
object BluetoothMessageParser {

    private const val PREFIX_MEDICINE_ALERT = "💊"
    private const val PREFIX_CONFIRM = "✅"
    private const val PREFIX_REALERT = "⚠️"
    private const val PREFIX_DAILY_RESET = "📅"
    private const val PREFIX_SCHEDULE_INFO = "📋"

    private const val KEYWORD_TAKEN = "복용 완료!"
    private const val KEYWORD_SETTING = "설정 완료"

    /**
     * 개행문자 기반으로 raw 메시지를 개별 메시지로 분리한다.
     *
     * @param raw 수신된 원시 문자열 (개행문자 포함 가능)
     * @return 비어있지 않은 개별 메시지 리스트 (원본 순서 유지)
     */
    fun splitMessages(raw: String): List<String> {
        return raw.split("\n").filter { it.isNotEmpty() }
    }

    /**
     * 접두사 기반으로 단일 메시지를 BluetoothMessage 도메인 모델로 변환한다.
     * 인식 불가 메시지는 Unknown 타입으로 반환하며 예외를 발생시키지 않는다.
     *
     * @param rawMessage 개별 메시지 문자열
     * @return 분류된 BluetoothMessage 객체
     */
    fun parse(rawMessage: String): BluetoothMessage {
        val trimmed = rawMessage.trim()

        return when {
            trimmed.startsWith(PREFIX_MEDICINE_ALERT) -> parseMedicineAlert(trimmed)
            trimmed.startsWith(PREFIX_CONFIRM) -> parseConfirmation(trimmed)
            trimmed.startsWith(PREFIX_REALERT) -> parseRealertWarning(trimmed)
            trimmed.startsWith(PREFIX_DAILY_RESET) -> BluetoothMessage.DailyReset
            trimmed.startsWith(PREFIX_SCHEDULE_INFO) -> parseScheduleInfo(trimmed)
            else -> BluetoothMessage.Unknown(rawMessage)
        }
    }

    /**
     * "📋 칸N 약이름 시:분 [완료]/[대기]" 형식의 스케줄 응답을 Schedule 객체로 파싱한다.
     * 칸 번호는 1-based(칸1~칸3)에서 0-based(0~2)로 변환된다.
     *
     * @param line 📋 접두사를 포함한 스케줄 응답 문자열
     * @return 파싱 성공 시 Schedule 객체, 실패 시 null
     */
    fun parseScheduleResponse(line: String): Schedule? {
        return try {
            val trimmed = line.trim()
            if (!trimmed.startsWith(PREFIX_SCHEDULE_INFO)) return null

            // "📋 칸N 약이름 시:분 [완료]/[대기]" 형식 파싱
            // 📋 제거 후 공백으로 분리
            val content = trimmed.removePrefix(PREFIX_SCHEDULE_INFO).trim()

            // "칸N 약이름 시:분 [상태]" 파싱
            val regex = Regex("""칸(\d+)\s+(.+?)\s+(\d+):(\d+)\s+\[(.+?)]""")
            val matchResult = regex.find(content) ?: return null

            val (slotStr, name, hourStr, minuteStr, _) = matchResult.destructured

            val slotOneBased = slotStr.toIntOrNull() ?: return null
            val slot = slotOneBased - 1 // 1-based → 0-based 변환
            val hour = hourStr.toIntOrNull() ?: return null
            val minute = minuteStr.toIntOrNull() ?: return null

            Schedule(
                slotNumber = slot,
                medicineName = name,
                hour = hour,
                minute = minute
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * "💊" 접두사 메시지에서 약 이름을 추출하여 MedicineAlert를 생성한다.
     */
    private fun parseMedicineAlert(message: String): BluetoothMessage {
        val name = message.removePrefix(PREFIX_MEDICINE_ALERT).trim()
        return BluetoothMessage.MedicineAlert(medicineName = name)
    }

    /**
     * "✅" 접두사 메시지를 TakenConfirmation 또는 SettingConfirmation으로 분류한다.
     */
    private fun parseConfirmation(message: String): BluetoothMessage {
        return when {
            message.contains(KEYWORD_TAKEN) -> {
                val name = extractMedicineNameFromConfirmation(message)
                BluetoothMessage.TakenConfirmation(medicineName = name)
            }
            message.contains(KEYWORD_SETTING) -> {
                val name = extractMedicineNameFromSetting(message)
                val time = extractTimeFromSetting(message)
                BluetoothMessage.SettingConfirmation(medicineName = name, time = time)
            }
            else -> BluetoothMessage.Unknown(message)
        }
    }

    /**
     * "⚠️" 접두사 메시지에서 약 이름을 추출하여 RealertWarning을 생성한다.
     */
    private fun parseRealertWarning(message: String): BluetoothMessage {
        val name = message.removePrefix(PREFIX_REALERT).trim()
        return BluetoothMessage.RealertWarning(medicineName = name)
    }

    /**
     * "📋" 접두사 메시지를 ScheduleInfo로 파싱한다.
     */
    private fun parseScheduleInfo(message: String): BluetoothMessage {
        val content = message.removePrefix(PREFIX_SCHEDULE_INFO).trim()

        val regex = Regex("""칸(\d+)\s+(.+?)\s+(\d+):(\d+)\s+\[(.+?)]""")
        val matchResult = regex.find(content)
            ?: return BluetoothMessage.Unknown(message)

        val (slotStr, name, hourStr, minuteStr, statusStr) = matchResult.destructured

        val slotOneBased = slotStr.toIntOrNull() ?: return BluetoothMessage.Unknown(message)
        val slot = slotOneBased - 1 // 1-based → 0-based 변환
        val hour = hourStr.toIntOrNull() ?: return BluetoothMessage.Unknown(message)
        val minute = minuteStr.toIntOrNull() ?: return BluetoothMessage.Unknown(message)
        val isDone = statusStr == "완료"

        return BluetoothMessage.ScheduleInfo(
            slot = slot,
            name = name,
            hour = hour,
            minute = minute,
            isDone = isDone
        )
    }

    /**
     * 복용 완료 메시지에서 약 이름을 추출한다.
     * 예: "✅ 혈압약 복용 완료!" → "혈압약"
     */
    private fun extractMedicineNameFromConfirmation(message: String): String {
        val content = message.removePrefix(PREFIX_CONFIRM).trim()
        val takenIndex = content.indexOf(KEYWORD_TAKEN)
        return if (takenIndex > 0) {
            content.substring(0, takenIndex).trim()
        } else {
            content
        }
    }

    /**
     * 설정 완료 메시지에서 약 이름을 추출한다.
     * 예: "✅ 혈압약 9:00 설정 완료" → "혈압약"
     */
    private fun extractMedicineNameFromSetting(message: String): String {
        val content = message.removePrefix(PREFIX_CONFIRM).trim()
        // "약이름 시:분 설정 완료" 패턴에서 약이름 추출
        val parts = content.split("\\s+".toRegex())
        return if (parts.size >= 3) {
            parts[0]
        } else {
            content.removeSuffix(KEYWORD_SETTING).trim()
        }
    }

    /**
     * 설정 완료 메시지에서 시간 정보를 추출한다.
     * 예: "✅ 혈압약 9:00 설정 완료" → "9:00"
     */
    private fun extractTimeFromSetting(message: String): String {
        val content = message.removePrefix(PREFIX_CONFIRM).trim()
        val timeRegex = Regex("""(\d+:\d+)""")
        val match = timeRegex.find(content)
        return match?.value ?: ""
    }
}
