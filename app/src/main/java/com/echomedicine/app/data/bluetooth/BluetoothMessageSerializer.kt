package com.echomedicine.app.data.bluetooth

import com.echomedicine.app.domain.model.Schedule

/**
 * Arduino_Master가 인식하는 명령 문자열로 Schedule 도메인 모델을 직렬화한다.
 *
 * SET 명령 형식: "SET:{slotNumber}:{medicineName}:{hour}:{minute}\n"
 * GET 명령 형식: "GET\n"
 */
object BluetoothMessageSerializer {

    private const val SLOT_MIN = 0
    private const val SLOT_MAX = 2
    private const val HOUR_MIN = 0
    private const val HOUR_MAX = 23
    private const val MINUTE_MIN = 0
    private const val MINUTE_MAX = 59
    private const val MEDICINE_NAME_MAX_BYTES = 15

    /**
     * Schedule 객체를 SET 명령 문자열로 직렬화한다.
     * 유효성 검증 실패 시 Result.failure를 반환한다.
     *
     * @param schedule 직렬화할 Schedule 객체
     * @return 성공 시 "SET:{slotNumber}:{medicineName}:{hour}:{minute}\n" 형식의 문자열,
     *         실패 시 IllegalArgumentException을 포함한 Result.failure
     */
    fun serializeSetCommand(schedule: Schedule): Result<String> {
        // slotNumber 검증: 0~2 범위
        if (schedule.slotNumber !in SLOT_MIN..SLOT_MAX) {
            return Result.failure(
                IllegalArgumentException(
                    "slotNumber must be in $SLOT_MIN..$SLOT_MAX, but was ${schedule.slotNumber}"
                )
            )
        }

        // medicineName 비공백 검증
        if (schedule.medicineName.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "medicineName must not be blank"
                )
            )
        }

        // medicineName UTF-8 바이트 길이 검증: 15바이트 이하
        val nameBytes = schedule.medicineName.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > MEDICINE_NAME_MAX_BYTES) {
            return Result.failure(
                IllegalArgumentException(
                    "medicineName UTF-8 byte length must be <= $MEDICINE_NAME_MAX_BYTES, but was ${nameBytes.size}"
                )
            )
        }

        // hour 검증: 0~23 범위
        if (schedule.hour !in HOUR_MIN..HOUR_MAX) {
            return Result.failure(
                IllegalArgumentException(
                    "hour must be in $HOUR_MIN..$HOUR_MAX, but was ${schedule.hour}"
                )
            )
        }

        // minute 검증: 0~59 범위
        if (schedule.minute !in MINUTE_MIN..MINUTE_MAX) {
            return Result.failure(
                IllegalArgumentException(
                    "minute must be in $MINUTE_MIN..$MINUTE_MAX, but was ${schedule.minute}"
                )
            )
        }

        val command = "SET:${schedule.slotNumber}:${schedule.medicineName}:${schedule.hour}:${schedule.minute}\n"
        return Result.success(command)
    }

    /**
     * GET 명령 문자열을 반환한다.
     *
     * @return "GET\n"
     */
    fun serializeGetCommand(): String {
        return "GET\n"
    }
}
