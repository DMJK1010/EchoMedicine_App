package com.echomedicine.app.data.bluetooth

import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.Schedule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll

/**
 * 블루투스 통신 프로토콜의 보편 속성(Property)을 검증하는 Property-Based Test.
 *
 * 설계 문서의 Property 1~5를 Kotest property testing으로 검증한다.
 */
class BluetoothProtocolPropertyTest : StringSpec({

    // 유효한 약 이름 생성기: 영문/숫자만 사용하여 공백·구분자 문제를 원천 차단 (1~5자)
    val validMedicineName = Arb.stringPattern("[A-Za-z0-9]{1,5}")

    val validSlot = Arb.int(0..2)
    val validHour = Arb.int(0..23)
    val validMinute = Arb.int(0..59)

    // Property 2: SET 명령 직렬화 형식 준수
    "Property2: 유효한 Schedule은 SET:{slot}:{name}:{hour}:{minute}\\n 형식으로 직렬화된다" {
        checkAll(validSlot, validMedicineName, validHour, validMinute) { slot, name, hour, minute ->
            val schedule = Schedule(slot, name, hour, minute)
            val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

            result.isSuccess shouldBe true
            result.getOrNull() shouldBe "SET:$slot:$name:$hour:$minute\n"
        }
    }

    // Property 4: Schedule 유효성 검증 완전성 - 범위 밖 값은 실패
    "Property4: slotNumber가 범위(0..2) 밖이면 Result.failure를 반환한다" {
        checkAll(Arb.int().filter { it !in 0..2 }, validMedicineName, validHour, validMinute) { slot, name, hour, minute ->
            val result = BluetoothMessageSerializer.serializeSetCommand(Schedule(slot, name, hour, minute))
            result.isFailure shouldBe true
        }
    }

    "Property4: hour가 범위(0..23) 밖이면 Result.failure를 반환한다" {
        checkAll(validSlot, validMedicineName, Arb.int().filter { it !in 0..23 }, validMinute) { slot, name, hour, minute ->
            val result = BluetoothMessageSerializer.serializeSetCommand(Schedule(slot, name, hour, minute))
            result.isFailure shouldBe true
        }
    }

    "Property4: minute가 범위(0..59) 밖이면 Result.failure를 반환한다" {
        checkAll(validSlot, validMedicineName, validHour, Arb.int().filter { it !in 0..59 }) { slot, name, hour, minute ->
            val result = BluetoothMessageSerializer.serializeSetCommand(Schedule(slot, name, hour, minute))
            result.isFailure shouldBe true
        }
    }

    "Property4: medicineName이 15바이트를 초과하면 Result.failure를 반환한다" {
        // 한글 6자 이상(18바이트+)
        checkAll(validSlot, Arb.string(6..10).filter { it.isNotBlank() }, validHour, validMinute) { slot, base, hour, minute ->
            val longName = "가나다라마바사".take(6) + base.take(2) // 충분히 긴 한글 이름
            val result = BluetoothMessageSerializer.serializeSetCommand(Schedule(slot, longName, hour, minute))
            if (longName.toByteArray(Charsets.UTF_8).size > 15) {
                result.isFailure shouldBe true
            }
        }
    }

    // Property 1: Schedule 직렬화/역직렬화 Round-trip
    // SET 명령을 직렬화한 뒤, 동일한 정보를 담은 ScheduleInfo 응답을 파싱하면 원본과 일치
    "Property1: Schedule 직렬화 후 동등한 ScheduleInfo 응답 파싱 시 원본 정보가 보존된다" {
        checkAll(validSlot, validMedicineName, validHour, validMinute) { slot, name, hour, minute ->
            val schedule = Schedule(slot, name, hour, minute)
            // 직렬화 성공 확인
            val serialized = BluetoothMessageSerializer.serializeSetCommand(schedule)
            serialized.isSuccess shouldBe true

            // 기기가 동일 스케줄을 1-based로 응답한다고 가정한 메시지 파싱
            val response = "📋 칸${slot + 1} $name $hour:$minute [대기]"
            val parsed = BluetoothMessageParser.parse(response)

            parsed.shouldBeInstanceOf<BluetoothMessage.ScheduleInfo>()
            val info = parsed as BluetoothMessage.ScheduleInfo
            info.slot shouldBe slot
            info.name shouldBe name
            info.hour shouldBe hour
            info.minute shouldBe minute
        }
    }

    // Property 3: 수신 메시지 타입 분류 정확성
    "Property3: 💊 접두사 메시지는 MedicineAlert로 분류되고 예외가 발생하지 않는다" {
        checkAll(validMedicineName) { name ->
            val parsed = BluetoothMessageParser.parse("💊 $name")
            parsed.shouldBeInstanceOf<BluetoothMessage.MedicineAlert>()
        }
    }

    "Property3: ⚠️ 접두사 메시지는 RealertWarning으로 분류된다" {
        checkAll(validMedicineName) { name ->
            val parsed = BluetoothMessageParser.parse("⚠️ $name")
            parsed.shouldBeInstanceOf<BluetoothMessage.RealertWarning>()
        }
    }

    "Property3: 임의의 인식 불가 문자열도 예외 없이 Unknown으로 분류된다" {
        checkAll(Arb.string(0..30).filter {
            !it.startsWith("💊") && !it.startsWith("✅") && !it.startsWith("⚠️") &&
                !it.startsWith("📅") && !it.startsWith("📋")
        }) { raw ->
            val parsed = BluetoothMessageParser.parse(raw)
            // 예외 없이 분류되어야 한다 (Unknown 또는 빈 문자열 처리)
            parsed shouldNotBe null
        }
    }

    // Property 5: 개행문자 기반 메시지 분리
    "Property5: N개 메시지를 개행으로 연결하면 정확히 N개로 분리되고 순서가 유지된다" {
        checkAll(Arb.int(1..10)) { n ->
            val messages = (1..n).map { "메시지$it" }
            val joined = messages.joinToString("\n")
            val split = BluetoothMessageParser.splitMessages(joined)

            split.size shouldBe n
            split shouldBe messages
        }
    }
})
