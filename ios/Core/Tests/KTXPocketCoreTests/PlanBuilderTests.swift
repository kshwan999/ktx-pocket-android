import Foundation
import XCTest
@testable import KTXPocketCore

final class PlanBuilderTests: XCTestCase {
    let now = Date(timeIntervalSince1970: 4_000_000_000)
    func condition(_ departure: String = "서울", arrival: String = "부산") -> SearchCondition {
        SearchCondition(departure: departure, arrival: arrival, start: now.addingTimeInterval(7200), end: now.addingTimeInterval(14400))
    }
    func settings(extra: Int = 30) -> AccessSettings {
        var result = AccessSettings(); result.enabled = true
        result.stations = [StationAccess(name: "서울", minutes: 20), StationAccess(name: "수서", minutes: 50), StationAccess(name: "광명", minutes: 51)]
        result.extraMinutes = extra
        return result
    }

    func testDisabledAccessKeepsManualConditionsAndDeduplicates() throws {
        let primary = condition()
        let plans = try PlanBuilder.make(primary: primary, alternatives: [primary, condition(arrival: "동대구")], access: AccessSettings(), now: now)
        XCTAssertEqual(plans.count, 2); XCTAssertNil(plans[0].accessMinutes)
    }
    func testInclusiveLimitIncludesSuseoAndRejectsSlowerStation() throws {
        let plans = try PlanBuilder.make(primary: condition(), alternatives: [], access: settings(), now: now)
        XCTAssertEqual(plans.map(\.condition.departure), ["서울", "수서"])
    }
    func testManualAlternativesTakePriorityAndTotalNeverExceedsThree() throws {
        let plans = try PlanBuilder.make(primary: condition(), alternatives: [condition(arrival: "동대구"), condition(arrival: "대전")], access: settings(extra: 60), now: now)
        XCTAssertEqual(plans.count, 3)
        XCTAssertEqual(plans.map(\.condition.arrival), ["부산", "동대구", "대전"])
    }
    func testFasterStationsAllowedWithoutExtraAndSortedByAccess() throws {
        var access = settings(extra: 0)
        access.stations[1].minutes = 10; access.stations[2].minutes = 15
        let plans = try PlanBuilder.make(primary: condition(), alternatives: [], access: access, now: now)
        XCTAssertEqual(plans.map(\.condition.departure), ["서울", "수서", "광명"])
    }
    func testUnreachableBaselineIsExcludedWhileReachableAlternativeRemains() throws {
        var primary = condition(); primary.start = now.addingTimeInterval(1); primary.end = now.addingTimeInterval(2400)
        var access = settings(); access.stations[1].minutes = 10; access.stations[0].minutes = 50
        let plans = try PlanBuilder.make(primary: primary, alternatives: [], access: access, now: now)
        XCTAssertEqual(plans.map(\.condition.departure), ["수서"])
    }
    func testMissingStationDuplicateAndInvalidNumbersAreRejected() {
        var missing = settings(); missing.stations[0].name = "용산"
        var duplicate = settings(); duplicate.stations[1].name = " 서울 "
        var negative = settings(); negative.stations[1].minutes = -1
        var over = settings(); over.boardingMinutes = 121
        var blank = settings(); blank.stations[0].minutes = nil
        for access in [missing, duplicate, negative, over, blank] {
            XCTAssertThrowsError(try PlanBuilder.make(primary: condition(), alternatives: [], access: access, now: now))
        }
        XCTAssertThrowsError(try PlanBuilder.make(primary: condition(), alternatives: [condition("용산")], access: settings(), now: now))
    }
    func testInvalidPassengerRouteDatesAndTooManyConditionsAreRejected() {
        var same = condition(); same.arrival = "서울"
        var passengers = condition(); passengers.adults = 7
        var backwards = condition(); backwards.end = backwards.start
        var expired = condition(); expired.end = now
        for value in [same, passengers, backwards, expired] { XCTAssertThrowsError(try value.validated(now: now)) }
        XCTAssertThrowsError(try PlanBuilder.make(primary: condition(), alternatives: [condition(), condition(), condition()], access: AccessSettings(), now: now))
    }
    func testEarliestDepartureIncludesTravelBufferAndRecomputesAsTimePasses() throws {
        var value = condition(); value.start = now
        let plan = try PlanBuilder.make(primary: value, alternatives: [], access: settings(), now: now)[0]
        XCTAssertEqual(plan.earliestDeparture(now: now), now.addingTimeInterval(35 * 60 + 5))
        XCTAssertEqual(plan.earliestDeparture(now: now.addingTimeInterval(86400)), now.addingTimeInterval(86400 + 35 * 60 + 5))
    }
    func testNoPlanWhenAllStationsAreUnreachable() {
        var value = condition(); value.start = now; value.end = now.addingTimeInterval(600)
        XCTAssertThrowsError(try PlanBuilder.make(primary: value, alternatives: [], access: settings(), now: now))
    }
    func testConfirmedPlanIsAValueSnapshot() throws {
        var value = condition(); var access = settings()
        let plans = try PlanBuilder.make(primary: value, alternatives: [], access: access, now: now)
        value.departure = "용산"; access.stations[0].minutes = 360
        XCTAssertEqual(plans[0].condition.departure, "서울"); XCTAssertEqual(plans[0].accessMinutes, 20)
    }
}
