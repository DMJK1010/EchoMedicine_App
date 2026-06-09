package com.echomedicine.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.echomedicine.app.domain.model.Schedule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MedicineAlarmScheduler 단위 테스트 (JUnit4 + Robolectric + MockK).
 *
 * AlarmManager에 정확한 알람이 등록/취소되는지 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MedicineAlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: MedicineAlarmScheduler

    @Before
    fun setup() {
        alarmManager = mockk(relaxed = true)
        every { alarmManager.canScheduleExactAlarms() } returns true

        context = mockk(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.packageName } returns "com.echomedicine.app"

        scheduler = MedicineAlarmScheduler(context)
    }

    @Test
    fun `유효한 스케줄에 대해 정확한 알람을 등록한다`() {
        scheduler.schedule(Schedule(0, "혈압약", 9, 0))

        verify {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                any(),
                any<PendingIntent>()
            )
        }
    }

    @Test
    fun `약 이름이 비어있으면 정확한 알람을 등록하지 않는다`() {
        scheduler.schedule(Schedule(1, "", 9, 0))

        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun `정확한 알람 권한이 없으면 부정확 알람으로 폴백한다`() {
        every { alarmManager.canScheduleExactAlarms() } returns false

        scheduler.schedule(Schedule(0, "약", 8, 30))

        verify {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                any(),
                any<PendingIntent>()
            )
        }
    }

    @Test
    fun `scheduleAll은 여러 스케줄을 모두 등록한다`() {
        scheduler.scheduleAll(
            listOf(
                Schedule(0, "약A", 9, 0),
                Schedule(1, "약B", 12, 30),
                Schedule(2, "약C", 18, 0)
            )
        )

        verify(exactly = 3) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }
}
