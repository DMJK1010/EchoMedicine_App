package com.echomedicine.app.data.bluetooth

import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.Schedule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class BluetoothMessageParserTest : DescribeSpec({

    describe("parse - 접두사 기반 메시지 분류") {

        describe("MedicineAlert (💊)") {
            it("should parse medicine alert message") {
                val result = BluetoothMessageParser.parse("💊 혈압약")

                result.shouldBeInstanceOf<BluetoothMessage.MedicineAlert>()
                result.medicineName shouldBe "혈압약"
            }

            it("should handle medicine alert with extra spaces") {
                val result = BluetoothMessageParser.parse("💊  비타민C ")

                result.shouldBeInstanceOf<BluetoothMessage.MedicineAlert>()
                result.medicineName shouldBe "비타민C"
            }
        }

        describe("TakenConfirmation (✅ ... 복용 완료!)") {
            it("should parse taken confirmation message") {
                val result = BluetoothMessageParser.parse("✅ 혈압약 복용 완료!")

                result.shouldBeInstanceOf<BluetoothMessage.TakenConfirmation>()
                result.medicineName shouldBe "혈압약"
            }

            it("should parse taken confirmation with different medicine name") {
                val result = BluetoothMessageParser.parse("✅ 비타민 복용 완료!")

                result.shouldBeInstanceOf<BluetoothMessage.TakenConfirmation>()
                result.medicineName shouldBe "비타민"
            }
        }

        describe("SettingConfirmation (✅ ... 설정 완료)") {
            it("should parse setting confirmation message") {
                val result = BluetoothMessageParser.parse("✅ 혈압약 9:00 설정 완료")

                result.shouldBeInstanceOf<BluetoothMessage.SettingConfirmation>()
                result.medicineName shouldBe "혈압약"
                result.time shouldBe "9:00"
            }

            it("should parse setting confirmation with different time") {
                val result = BluetoothMessageParser.parse("✅ 비타민 14:30 설정 완료")

                result.shouldBeInstanceOf<BluetoothMessage.SettingConfirmation>()
                result.medicineName shouldBe "비타민"
                result.time shouldBe "14:30"
            }
        }

        describe("RealertWarning (⚠️)") {
            it("should parse realert warning message") {
                val result = BluetoothMessageParser.parse("⚠️ 혈압약")

                result.shouldBeInstanceOf<BluetoothMessage.RealertWarning>()
                result.medicineName shouldBe "혈압약"
            }

            it("should handle realert with trailing whitespace") {
                val result = BluetoothMessageParser.parse("⚠️ 비타민C ")

                result.shouldBeInstanceOf<BluetoothMessage.RealertWarning>()
                result.medicineName shouldBe "비타민C"
            }
        }

        describe("DailyReset (📅)") {
            it("should parse daily reset message") {
                val result = BluetoothMessageParser.parse("📅 자정 초기화")

                result shouldBe BluetoothMessage.DailyReset
            }

            it("should parse daily reset with just prefix") {
                val result = BluetoothMessageParser.parse("📅")

                result shouldBe BluetoothMessage.DailyReset
            }
        }

        describe("ScheduleInfo (📋)") {
            it("should parse schedule info with waiting status") {
                val result = BluetoothMessageParser.parse("📋 칸1 혈압약 9:00 [대기]")

                result.shouldBeInstanceOf<BluetoothMessage.ScheduleInfo>()
                result.slot shouldBe 0  // 1-based → 0-based
                result.name shouldBe "혈압약"
                result.hour shouldBe 9
                result.minute shouldBe 0
                result.isDone shouldBe false
            }

            it("should parse schedule info with done status") {
                val result = BluetoothMessageParser.parse("📋 칸2 비타민 14:30 [완료]")

                result.shouldBeInstanceOf<BluetoothMessage.ScheduleInfo>()
                result.slot shouldBe 1  // 1-based → 0-based
                result.name shouldBe "비타민"
                result.hour shouldBe 14
                result.minute shouldBe 30
                result.isDone shouldBe true
            }

            it("should parse schedule info for slot 3") {
                val result = BluetoothMessageParser.parse("📋 칸3 영양제 23:59 [대기]")

                result.shouldBeInstanceOf<BluetoothMessage.ScheduleInfo>()
                result.slot shouldBe 2  // 1-based → 0-based
                result.name shouldBe "영양제"
                result.hour shouldBe 23
                result.minute shouldBe 59
                result.isDone shouldBe false
            }

            it("should return Unknown for malformed schedule info") {
                val result = BluetoothMessageParser.parse("📋 잘못된형식")

                result.shouldBeInstanceOf<BluetoothMessage.Unknown>()
            }
        }

        describe("MedicinePresence (📦)") {
            it("should parse presence available message") {
                val result = BluetoothMessageParser.parse("📦 칸1 혈압약 있음")

                result.shouldBeInstanceOf<BluetoothMessage.MedicinePresence>()
                result.slot shouldBe 0  // 1-based → 0-based
                result.medicineName shouldBe "혈압약"
                result.present shouldBe true
            }

            it("should parse presence empty message") {
                val result = BluetoothMessageParser.parse("📦 칸3 비타민 없음")

                result.shouldBeInstanceOf<BluetoothMessage.MedicinePresence>()
                result.slot shouldBe 2
                result.medicineName shouldBe "비타민"
                result.present shouldBe false
            }
        }

        describe("ScheduleInfo presence (📋 [약있음]/[약없음])") {
            it("should parse schedule info with medicine present") {
                val result = BluetoothMessageParser.parse("📋 칸1 혈압약 9:00 [약있음]")

                result.shouldBeInstanceOf<BluetoothMessage.ScheduleInfo>()
                result.present shouldBe true
            }

            it("should parse schedule info with medicine absent") {
                val result = BluetoothMessageParser.parse("📋 칸2 당뇨약 13:00 [약없음]")

                result.shouldBeInstanceOf<BluetoothMessage.ScheduleInfo>()
                result.present shouldBe false
            }
        }

        describe("Unknown") {
            it("should return Unknown for unrecognized messages") {
                val result = BluetoothMessageParser.parse("알 수 없는 메시지")

                result.shouldBeInstanceOf<BluetoothMessage.Unknown>()
                result.raw shouldBe "알 수 없는 메시지"
            }

            it("should return Unknown for empty string") {
                val result = BluetoothMessageParser.parse("")

                result.shouldBeInstanceOf<BluetoothMessage.Unknown>()
                result.raw shouldBe ""
            }

            it("should not throw exception for any input") {
                val result = BluetoothMessageParser.parse("!@#\$%^&*()")

                result.shouldBeInstanceOf<BluetoothMessage.Unknown>()
            }

            it("should return Unknown for ✅ without known keyword") {
                val result = BluetoothMessageParser.parse("✅ 알 수 없는 확인")

                result.shouldBeInstanceOf<BluetoothMessage.Unknown>()
            }
        }
    }

    describe("parseScheduleResponse") {

        it("should parse valid schedule response with 1-based to 0-based conversion") {
            val result = BluetoothMessageParser.parseScheduleResponse("📋 칸1 혈압약 9:00 [대기]")

            result.shouldNotBeNull()
            result.slotNumber shouldBe 0
            result.medicineName shouldBe "혈압약"
            result.hour shouldBe 9
            result.minute shouldBe 0
        }

        it("should parse slot 2") {
            val result = BluetoothMessageParser.parseScheduleResponse("📋 칸2 비타민 14:30 [완료]")

            result.shouldNotBeNull()
            result.slotNumber shouldBe 1
            result.medicineName shouldBe "비타민"
            result.hour shouldBe 14
            result.minute shouldBe 30
        }

        it("should parse slot 3") {
            val result = BluetoothMessageParser.parseScheduleResponse("📋 칸3 영양제 0:05 [대기]")

            result.shouldNotBeNull()
            result.slotNumber shouldBe 2
            result.medicineName shouldBe "영양제"
            result.hour shouldBe 0
            result.minute shouldBe 5
        }

        it("should return null for message without 📋 prefix") {
            val result = BluetoothMessageParser.parseScheduleResponse("💊 혈압약")

            result.shouldBeNull()
        }

        it("should return null for malformed schedule response") {
            val result = BluetoothMessageParser.parseScheduleResponse("📋 잘못된 형식입니다")

            result.shouldBeNull()
        }

        it("should return null for empty string") {
            val result = BluetoothMessageParser.parseScheduleResponse("")

            result.shouldBeNull()
        }
    }

    describe("splitMessages - 개행문자 기반 메시지 분리") {

        it("should split messages by newline") {
            val raw = "💊 혈압약\n✅ 비타민 복용 완료!\n📅 자정 초기화"
            val result = BluetoothMessageParser.splitMessages(raw)

            result.size shouldBe 3
            result[0] shouldBe "💊 혈압약"
            result[1] shouldBe "✅ 비타민 복용 완료!"
            result[2] shouldBe "📅 자정 초기화"
        }

        it("should filter out empty parts") {
            val raw = "💊 혈압약\n\n✅ 비타민 복용 완료!\n"
            val result = BluetoothMessageParser.splitMessages(raw)

            result.size shouldBe 2
            result[0] shouldBe "💊 혈압약"
            result[1] shouldBe "✅ 비타민 복용 완료!"
        }

        it("should return single message when no newline") {
            val raw = "💊 혈압약"
            val result = BluetoothMessageParser.splitMessages(raw)

            result.size shouldBe 1
            result[0] shouldBe "💊 혈압약"
        }

        it("should return empty list for empty string") {
            val result = BluetoothMessageParser.splitMessages("")

            result.size shouldBe 0
        }

        it("should return empty list for string with only newlines") {
            val result = BluetoothMessageParser.splitMessages("\n\n\n")

            result.size shouldBe 0
        }

        it("should preserve message order") {
            val raw = "📋 칸1 혈압약 9:00 [대기]\n📋 칸2 비타민 14:30 [완료]\n📋 칸3 영양제 23:00 [대기]"
            val result = BluetoothMessageParser.splitMessages(raw)

            result.size shouldBe 3
            result[0] shouldBe "📋 칸1 혈압약 9:00 [대기]"
            result[1] shouldBe "📋 칸2 비타민 14:30 [완료]"
            result[2] shouldBe "📋 칸3 영양제 23:00 [대기]"
        }
    }
})
