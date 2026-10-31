import Foundation

public enum SeatPreference: String, Codable, CaseIterable, Sendable {
    case generalOnly, generalFirst, specialOnly, specialFirst

    public var title: String {
        switch self {
        case .generalOnly: "일반실만"
        case .generalFirst: "일반실 우선"
        case .specialOnly: "특실만"
        case .specialFirst: "특실 우선"
        }
    }
}

public struct InputError: LocalizedError, Sendable {
    public let errorDescription: String?
    public init(_ message: String) { errorDescription = message }
}

public struct SearchCondition: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID
    public var departure: String
    public var arrival: String
    public var start: Date
    public var end: Date
    public var adults: Int
    public var seat: SeatPreference

    public init(departure: String = "서울", arrival: String = "부산", start: Date = .now.addingTimeInterval(7200),
                end: Date = .now.addingTimeInterval(14400), adults: Int = 1, seat: SeatPreference = .generalOnly) {
        id = UUID()
        self.departure = departure; self.arrival = arrival; self.start = start; self.end = end
        self.adults = adults; self.seat = seat
    }

    public func validated(now: Date) throws -> SearchCondition {
        var result = self
        result.departure = departure.trimmingCharacters(in: .whitespacesAndNewlines)
        result.arrival = arrival.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !result.departure.isEmpty, !result.arrival.isEmpty, result.departure != result.arrival else {
            throw InputError("출발역과 도착역을 서로 다르게 입력해주세요.")
        }
        guard end > start, end > now else { throw InputError("종료 시각은 시작 시각과 현재 시각보다 뒤여야 해요.") }
        guard (1...6).contains(adults) else { throw InputError("어른 인원은 1~6명으로 선택해주세요.") }
        return result
    }

    public func hasSameCriteria(as other: SearchCondition) -> Bool {
        departure == other.departure && arrival == other.arrival && start == other.start && end == other.end
            && adults == other.adults && seat == other.seat
    }
}

public struct StationAccess: Codable, Identifiable, Equatable, Sendable {
    public var id = UUID()
    public var name: String
    public var minutes: Int?
    public init(name: String = "", minutes: Int? = nil) { self.name = name; self.minutes = minutes }
}

public struct AccessSettings: Codable, Equatable, Sendable {
    public var enabled = false
    public var stations = [StationAccess(name: "서울"), StationAccess(), StationAccess()]
    public var extraMinutes = 30
    public var boardingMinutes = 15
    public init() {}

    public func validatedTimes(baseline: String) throws -> [String: Int] {
        guard (0...360).contains(extraMinutes), (0...120).contains(boardingMinutes) else {
            throw InputError("추가 이동은 0~360분, 승차 여유는 0~120분으로 입력해주세요.")
        }
        var times: [String: Int] = [:]
        for station in stations {
            let name = station.name.trimmingCharacters(in: .whitespacesAndNewlines)
            if name.isEmpty && station.minutes == nil { continue }
            guard !name.isEmpty, let minutes = station.minutes, (0...360).contains(minutes) else {
                throw InputError("역 이름과 이동 시간(0~360분)을 함께 입력해주세요.")
            }
            guard times[name] == nil else { throw InputError("같은 역은 한 번만 등록해주세요.") }
            times[name] = minutes
        }
        guard (1...3).contains(times.count), times[baseline] != nil else {
            throw InputError("현재 출발역을 포함해 1~3개 역의 이동 시간을 등록해주세요.")
        }
        return times
    }
}

public struct SearchPlan: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID { condition.id }
    public let condition: SearchCondition
    public let accessMinutes: Int?
    public let boardingMinutes: Int

    public func earliestDeparture(now: Date) -> Date {
        max(condition.start, now.addingTimeInterval(Double((accessMinutes ?? 0) + boardingMinutes) * 60 + 5))
    }
}

public enum PlanBuilder {
    public static func make(primary: SearchCondition, alternatives: [SearchCondition], access: AccessSettings,
                            now: Date) throws -> [SearchPlan] {
        guard alternatives.count <= 2 else { throw InputError("추가 대안은 2개까지 등록해주세요.") }
        let primary = try primary.validated(now: now)
        var candidates = [primary]
        for other in alternatives {
            let clean = try other.validated(now: now)
            if !candidates.contains(where: { $0.hasSameCriteria(as: clean) }) { candidates.append(clean) }
        }
        guard access.enabled else {
            return candidates.map { SearchPlan(condition: $0, accessMinutes: nil, boardingMinutes: 0) }
        }
        let times = try access.validatedTimes(baseline: primary.departure)
        guard let baselineTime = times[primary.departure] else { throw InputError("기준 출발역을 확인해주세요.") }
        let limit = baselineTime + access.extraMinutes
        // A stable order makes the final confirmation reproducible when access times are equal.
        let eligible = times.keys.filter { times[$0, default: 0] <= limit }.sorted {
            if $0 == $1 { return false }
            if $0 == primary.departure { return true }
            if $1 == primary.departure { return false }
            return (times[$0, default: 0], $0) < (times[$1, default: 0], $1)
        }
        for station in eligible where station != primary.arrival {
            var candidate = primary
            candidate.id = UUID(); candidate.departure = station
            if !candidates.contains(where: { $0.hasSameCriteria(as: candidate) }) { candidates.append(candidate) }
        }
        var result: [SearchPlan] = []
        for condition in candidates {
            guard let minutes = times[condition.departure] else {
                throw InputError("\(condition.departure)역까지의 이동 시간을 등록해주세요.")
            }
            let plan = SearchPlan(condition: condition, accessMinutes: minutes, boardingMinutes: access.boardingMinutes)
            if minutes <= limit, plan.earliestDeparture(now: now) < condition.end, result.count < 3 { result.append(plan) }
        }
        guard !result.isEmpty else { throw InputError("이동 시간과 승차 여유를 확보할 수 없어요. 시간 범위를 넓혀주세요.") }
        return result
    }
}
