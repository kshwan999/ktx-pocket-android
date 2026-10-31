import Foundation
import XCTest
@testable import KTXPocketCore

final class ProbeRecordTests: XCTestCase {
    let now = Date(timeIntervalSince1970: 4_000_000_000)
    func record() throws -> ProbeRecord {
        let plans = try PlanBuilder.make(primary: SearchCondition(start: now.addingTimeInterval(7200), end: now.addingTimeInterval(14400)),
                                         alternatives: [], access: AccessSettings(), now: now)
        return try ProbeRecord(durationSeconds: 60, plans: plans, now: now)
    }
    func testNoSamplesBeforeStartAndCannotStartTwice() throws {
        var value = try record()
        value.sample(now: now, uptime: 100, visibility: .foreground)
        XCTAssertTrue(value.samples.isEmpty)
        XCTAssertTrue(value.start(now: now, uptime: 100)); XCTAssertFalse(value.start(now: now, uptime: 101))
    }
    func testMonotonicTimeIgnoresWallClockJumpAndRecordsRealBackgroundSamples() throws {
        var value = try record(); value.start(now: now, uptime: 100)
        value.sample(now: now.addingTimeInterval(-3600), uptime: 104, visibility: .foreground)
        value.sample(now: now.addingTimeInterval(7200), uptime: 108, visibility: .background)
        XCTAssertEqual(value.elapsedSeconds, 8); XCTAssertEqual(value.maxGapSeconds, 4)
        XCTAssertEqual(value.backgroundSamples, 1); XCTAssertEqual(value.delayedSamples, 0)
    }
    func testSuspensionGapIsVisibleAndCannotBeReportedAsSmoothExecution() throws {
        var value = try record(); value.start(now: now, uptime: 100)
        value.sample(now: now.addingTimeInterval(65), uptime: 165, visibility: .background)
        value.finish(.completed, now: now.addingTimeInterval(65), note: "test")
        XCTAssertEqual(value.delayedSamples, 1); XCTAssertEqual(value.maxGapSeconds, 65)
        XCTAssertTrue(value.assessment.contains("10초"))
    }
    func testForegroundOnlyCompletionDoesNotClaimBackgroundSuccess() throws {
        var value = try record(); value.start(now: now, uptime: 100)
        for seconds in stride(from: 4, through: 60, by: 4) {
            value.sample(now: now.addingTimeInterval(Double(seconds)), uptime: 100 + Double(seconds), visibility: .foreground)
        }
        value.finish(.completed, now: now.addingTimeInterval(60), note: "test")
        XCTAssertTrue(value.reachedDuration); XCTAssertTrue(value.assessment.contains("판단할 수 없어요"))
    }
    func testStoppedOrExpiredRecordCannotContinueOrCompleteAgain() throws {
        for phase in [ProbePhase.stopped, .expired] {
            var value = try record(); value.start(now: now, uptime: 100)
            XCTAssertTrue(value.finish(phase, now: now, note: "test"))
            value.sample(now: now, uptime: 104, visibility: .background)
            XCTAssertTrue(value.samples.isEmpty)
            XCTAssertFalse(value.finish(.completed, now: now, note: "test"))
            XCTAssertEqual(value.phase, phase)
        }
    }
    func testRoundTripAndRelaunchDetectInterruptionWithoutRestarting() throws {
        var value = try record(); value.start(now: now, uptime: 100)
        value.sample(now: now, uptime: 104, visibility: .background)
        var restored = try JSONDecoder().decode(ProbeRecord.self, from: JSONEncoder().encode(value))
        restored.recoverAfterRelaunch(now: now.addingTimeInterval(300))
        XCTAssertEqual(restored.phase, .interrupted); XCTAssertEqual(restored.samples.count, 1)
        XCTAssertEqual(restored.networkRequests, 0); XCTAssertTrue(restored.note.contains("확정할 수 없으며"))
        XCTAssertFalse(restored.start(now: now, uptime: 500))
    }
    func testNoUnmeasuredProgressAndProgressNeverExceedsOne() throws {
        var value = try record(); value.start(now: now, uptime: 100)
        XCTAssertEqual(value.progress, 0)
        value.sample(now: now, uptime: 200, visibility: .background)
        XCTAssertEqual(value.progress, 1)
    }
    func testInvalidDurationRejected() throws {
        let value = try record()
        XCTAssertThrowsError(try ProbeRecord(durationSeconds: 86400, plans: value.plans, now: now))
        XCTAssertThrowsError(try ProbeRecord(durationSeconds: 60, plans: [], now: now))
    }
}
