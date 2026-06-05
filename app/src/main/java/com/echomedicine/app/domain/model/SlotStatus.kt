package com.echomedicine.app.domain.model

enum class SlotStatus {
    WAITING,    // 대기 중
    TAKEN,      // 복용 완료
    MISSED,     // 미복용
    EMPTY       // 비어 있음 (약 미배정)
}
