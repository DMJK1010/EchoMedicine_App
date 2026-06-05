package com.echomedicine.app.data.bluetooth

import com.echomedicine.app.domain.model.Schedule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BluetoothMessageSerializerTest : DescribeSpec({

    describe("serializeSetCommand") {

        it("should serialize a valid Schedule to SET command format") {
            val schedule = Schedule(slotNumber = 0, medicineName = "혈압약", hour = 9, minute = 0)
            val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

            result.isSuccess shouldBe true
            result.getOrNull() shouldBe "SET:0:혈압약:9:0\n"
        }

        it("should serialize boundary values correctly") {
            val schedule = Schedule(slotNumber = 2, medicineName = "A", hour = 23, minute = 59)
            val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

            result.isSuccess shouldBe true
            result.getOrNull() shouldBe "SET:2:A:23:59\n"
        }

        it("should serialize schedule with hour 0 and minute 0") {
            val schedule = Schedule(slotNumber = 0, medicineName = "약", hour = 0, minute = 0)
            val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

            result.isSuccess shouldBe true
            result.getOrNull() shouldBe "SET:0:약:0:0\n"
        }

        describe("slotNumber validation") {
            it("should fail when slotNumber is negative") {
                val schedule = Schedule(slotNumber = -1, medicineName = "약", hour = 9, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }

            it("should fail when slotNumber is 3") {
                val schedule = Schedule(slotNumber = 3, medicineName = "약", hour = 9, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }
        }

        describe("hour validation") {
            it("should fail when hour is negative") {
                val schedule = Schedule(slotNumber = 0, medicineName = "약", hour = -1, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }

            it("should fail when hour is 24") {
                val schedule = Schedule(slotNumber = 0, medicineName = "약", hour = 24, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }
        }

        describe("minute validation") {
            it("should fail when minute is negative") {
                val schedule = Schedule(slotNumber = 0, medicineName = "약", hour = 9, minute = -1)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }

            it("should fail when minute is 60") {
                val schedule = Schedule(slotNumber = 0, medicineName = "약", hour = 9, minute = 60)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }
        }

        describe("medicineName validation") {
            it("should fail when medicineName is empty") {
                val schedule = Schedule(slotNumber = 0, medicineName = "", hour = 9, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }

            it("should fail when medicineName is whitespace only") {
                val schedule = Schedule(slotNumber = 0, medicineName = "   ", hour = 9, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }

            it("should fail when medicineName exceeds 15 UTF-8 bytes") {
                // Korean characters are 3 bytes each in UTF-8, so 6 chars = 18 bytes > 15
                val schedule = Schedule(slotNumber = 0, medicineName = "아침저녁혈압약", hour = 9, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isFailure shouldBe true
                result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
            }

            it("should succeed when medicineName is exactly 15 UTF-8 bytes") {
                // Korean characters are 3 bytes each, so 5 chars = 15 bytes
                val schedule = Schedule(slotNumber = 0, medicineName = "혈압약먹자", hour = 9, minute = 0)
                val result = BluetoothMessageSerializer.serializeSetCommand(schedule)

                result.isSuccess shouldBe true
            }
        }
    }

    describe("serializeGetCommand") {
        it("should return GET command with newline") {
            BluetoothMessageSerializer.serializeGetCommand() shouldBe "GET\n"
        }
    }
})
