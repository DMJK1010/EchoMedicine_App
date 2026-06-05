# EchoMedicine - 스마트 복약 알림 Android 앱

Arduino 기반 복약 알림 보관함과 Bluetooth Classic SPP로 통신하는 네이티브 Android 앱입니다.

## 주요 기능

- **블루투스 연결** — 페어링된 Arduino 보관함과 SPP 프로토콜로 연결/재연결
- **스케줄 설정** — 3개 Slot에 약 이름과 복용 시간 설정 (SET 명령)
- **실시간 알림** — 복용 시간 도래, 복용 완료, 미복용 경고 Notification
- **복용 이력** — 최근 90일간 복용/미복용 기록 조회 및 복용률 표시
- **대시보드** — 3개 Slot 실시간 상태 (WAITING / TAKEN / MISSED / EMPTY)
- **백그라운드 동작** — Foreground Service로 앱 종료 후에도 알림 수신

## 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Kotlin 1.9.22 |
| 아키텍처 | MVVM + Clean Architecture |
| DI | Hilt 2.50 |
| 데이터베이스 | Room 2.6.1 |
| 비동기 | Kotlin Coroutines 1.7.3 + Flow |
| 화면 전환 | Navigation Component 2.7.6 |
| UI | Material Design 3, ViewBinding |
| 설정 저장 | DataStore Preferences |
| 테스트 | Kotest 5.8.0, MockK 1.13.9, Turbine 1.0.0 |
| 빌드 | Gradle 8.7, AGP 8.2.2, KSP 1.9.22 |

## 빌드 환경 요구사항

### 필수 소프트웨어

| 항목 | 버전 | 비고 |
|------|------|------|
| **JDK** | 17 이상 | `compileOptions`에서 Java 17 사용 |
| **Android Studio** | Hedgehog (2023.1.1) 이상 | AGP 8.2.2 호환 |
| **Android SDK** | API 34 (compileSdk) | SDK Manager에서 설치 |
| **Android SDK Build-Tools** | 34.x | SDK Manager에서 설치 |
| **Gradle** | 8.7 | Wrapper 포함 (별도 설치 불필요) |

### Android SDK 구성

SDK Manager에서 아래 항목을 설치해야 합니다:

- **SDK Platforms:** Android 14 (API 34)
- **SDK Tools:**
  - Android SDK Build-Tools 34
  - Android SDK Platform-Tools
  - Android Emulator (에뮬레이터 테스트 시)

### 최소/타겟 SDK

- `minSdk`: 26 (Android 8.0 Oreo)
- `targetSdk`: 34 (Android 14)

### 환경 변수

```
ANDROID_HOME = <Android SDK 설치 경로>
JAVA_HOME = <JDK 17 설치 경로>
```

## 빌드 방법

```bash
# 프로젝트 클론
git clone https://github.com/DMJK1010/EchoMedicine_App.git
cd EchoMedicine_App

# 디버그 빌드
./gradlew assembleDebug

# 릴리즈 빌드 (서명 키 필요)
./gradlew assembleRelease

# 단위 테스트 실행
./gradlew testDebugUnitTest

# APK 위치
# app/build/outputs/apk/debug/app-debug.apk
```

Windows 환경에서는 `./gradlew` 대신 `gradlew.bat`을 사용합니다.

## 프로젝트 구조

```
app/src/main/java/com/echomedicine/app/
├── data/
│   ├── bluetooth/          # BT 프로토콜 (Parser, Serializer, ConnectionManager)
│   ├── db/                 # Room Database, DAO, Entity
│   ├── preference/         # DataStore 설정
│   ├── repository/         # Repository 구현체
│   └── sync/               # 실시간 메시지 동기화 (MessageSyncManager)
├── di/                     # Hilt DI 모듈
├── domain/
│   ├── model/              # 도메인 모델 (Schedule, MedicineSlot, BluetoothMessage 등)
│   ├── repository/         # Repository 인터페이스
│   └── usecase/            # UseCase 클래스
├── presentation/
│   ├── connection/         # 블루투스 연결 화면
│   ├── dashboard/          # 대시보드 (Slot 상태, 복용률)
│   ├── history/            # 복용 이력 화면
│   ├── schedule/           # 스케줄 설정 화면
│   └── settings/           # 설정 화면
├── service/                # BluetoothForegroundService
├── util/                   # NotificationHelper, PermissionHelper
└── EchoMedicineApp.kt      # Application (Hilt)
```

## 블루투스 통신 프로토콜

### 앱 → Arduino (송신)

| 명령 | 형식 | 설명 |
|------|------|------|
| SET | `SET:{slot}:{name}:{hour}:{minute}\n` | 스케줄 설정 (slot: 0~2) |
| GET | `GET\n` | 현재 스케줄 조회 |

### Arduino → 앱 (수신)

| 접두사 | 의미 |
|--------|------|
| ✅ | 설정 확인 응답 |
| 💊 | 복용 완료 알림 |
| ⏰ | 복용 시간 알림 |
| ⚠️ | 미복용 경고 |
| 📋 | 스케줄 조회 응답 |
| 📅 | 자정 초기화 |

## 권한

| 권한 | 용도 | 조건 |
|------|------|------|
| BLUETOOTH | BT 통신 | API < 31 |
| BLUETOOTH_ADMIN | BT 관리 | API < 31 |
| BLUETOOTH_CONNECT | BT 연결 | API 31+ |
| BLUETOOTH_SCAN | BT 검색 | API 31+ |
| ACCESS_FINE_LOCATION | BT 스캔 | API < 31 |
| POST_NOTIFICATIONS | 알림 표시 | API 33+ |
| FOREGROUND_SERVICE | 백그라운드 동작 | 전체 |

## 테스트

```bash
# 전체 단위 테스트
./gradlew testDebugUnitTest

# 특정 테스트 클래스 실행
./gradlew test --tests "com.echomedicine.app.data.bluetooth.BluetoothMessageSerializerTest"
./gradlew test --tests "com.echomedicine.app.data.sync.MessageSyncManagerTest"
```

테스트 프레임워크:
- **Kotest** — Property-based testing 및 BDD 스타일 테스트
- **MockK** — Kotlin 네이티브 모킹
- **Turbine** — Flow/StateFlow 테스트

## 라이선스

이 프로젝트는 학술 목적으로 개발되었습니다.
