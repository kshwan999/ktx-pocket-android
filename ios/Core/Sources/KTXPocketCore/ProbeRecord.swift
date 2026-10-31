import Foundation

public enum ProbePhase: String, Codable, Sendable {
    case requested, running, completed, stopped, expired, interrupted, failed
    public var isActive: Bool { self == .requested || self == .running }
    public var title: String {
        switch self {
        case .requested: "실행 요청 중"
        case .running: "실행 간격 측정 중"
        case .completed: "실행 시험 완료"
        case .stopped: "직접 중지함"
        case .expired: "시스템에서 중단됨"
        case .interrupted: "이전 실행이 종료됨"
        case .failed: "실행 시험 오류"
        }
    }
}

public enum AppVisibility: String, Codable, Sendable { case foreground, inactive, background }

public struct ProbeSample: Codable, Identifiable, Sendable {
    public var id: Int { sequence }
    public let sequence: Int
    public let timestamp: Date
    public let elapsedSeconds: TimeInterval
    public let gapSeconds: TimeInterval
    public let visibility: AppVisibility
}

public struct ProbeRecord: Codable, Identifiable, Sendable {
    public let id: UUID
    public private(set) var phase: ProbePhase
    public let requestedAt: Date
    public private(set) var startedAt: Date?
    public private(set) var endedAt: Date?
    public private(set) var detectedInterruptionAt: Date?
    public let durationSeconds: Int
    public let plans: [SearchPlan]
    public private(set) var samples: [ProbeSample] = []
    public private(set) var note = ""
    public let networkRequests: Int
    public let purpose: String
    private var startedUptime: TimeInterval?
    private var lastUptime: TimeInterval?

    public init(durationSeconds: Int, plans: [SearchPlan], now: Date) throws {
        guard [60, 300, 900, 1800].contains(durationSeconds) else { throw InputError("시험 시간은 1·5·15·30분 중 선택해주세요.") }
        guard (1...3).contains(plans.count) else { throw InputError("시험 조건은 1~3개로 확인해주세요.") }
        id = UUID(); phase = .requested; requestedAt = now; self.durationSeconds = durationSeconds; self.plans = plans
        networkRequests = 0
        purpose = "iOS 백그라운드 실행 간격 측정. 실제 코레일 조회·예약은 실행하지 않습니다."
    }

    @discardableResult public mutating func start(now: Date, uptime: TimeInterval) -> Bool {
        guard phase == .requested else { return false }
        phase = .running; startedAt = now; startedUptime = uptime; lastUptime = uptime
        return true
    }

    public mutating func sample(now: Date, uptime: TimeInterval, visibility: AppVisibility) {
        guard phase == .running, let start = startedUptime, let last = lastUptime else { return }
        guard uptime >= last else { finish(.interrupted, now: now, note: "기기의 실행 시간 기준이 변경되어 측정을 종료했습니다."); return }
        samples.append(ProbeSample(sequence: samples.count + 1, timestamp: now, elapsedSeconds: uptime - start,
                                   gapSeconds: uptime - last, visibility: visibility))
        lastUptime = uptime
    }

    @discardableResult public mutating func finish(_ phase: ProbePhase, now: Date, note: String) -> Bool {
        guard self.phase.isActive, !phase.isActive else { return false }
        self.phase = phase; endedAt = now; self.note = note
        return true
    }

    public mutating func recoverAfterRelaunch(now: Date) {
        guard phase.isActive else { return }
        phase = .interrupted
        detectedInterruptionAt = now
        endedAt = nil
        note = "이전 실행이 완료되지 않은 상태로 앱이 다시 열렸습니다. 중단 시각과 원인은 확정할 수 없으며 자동 재개하지 않습니다."
    }

    public var elapsedSeconds: TimeInterval { samples.last?.elapsedSeconds ?? 0 }
    public var maxGapSeconds: TimeInterval { samples.map(\.gapSeconds).max() ?? 0 }
    public var backgroundSamples: Int { samples.filter { $0.visibility == .background }.count }
    public var delayedSamples: Int { samples.filter { $0.gapSeconds > 10 }.count }
    public var reachedDuration: Bool { elapsedSeconds >= Double(durationSeconds) }
    public var progress: Double { min(1, max(0, elapsedSeconds / Double(durationSeconds))) }

    public var assessment: String {
        if phase.isActive { return "측정 중입니다. 실제 열차 조회·예약은 실행하지 않아요." }
        if backgroundSamples == 0 { return "백그라운드에서 기록된 표본이 없어 잠금 상태의 실행 여부를 판단할 수 없어요." }
        if delayedSamples > 0 { return "10초를 넘는 실행 간격이 관측됐어요. 연속 조회에 적합한지는 추가 확인이 필요합니다." }
        if phase == .completed, reachedDuration { return "이번 시험에서 화면 밖 실행 표본을 기록했어요. 실제 통신이나 더 긴 실행을 보장하는 결과는 아닙니다." }
        return "시험이 끝나기 전에 중단됐어요. 기록된 시각과 실행 간격을 확인해주세요."
    }
}
