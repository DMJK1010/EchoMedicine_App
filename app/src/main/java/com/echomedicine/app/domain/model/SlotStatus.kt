package com.echomedicine.app.domain.model

enum class SlotStatus {
    WAITING,    // 대기 중 (복용 예정 시각 이전)
    DUE,        // 복용 시간 (예정 시각 ~ 이후 일정 시간)
    TAKEN,      // 복용 완료
    MISSED,     // 미복용
    EMPTY       // 비어 있음 (약 미배정)
}
