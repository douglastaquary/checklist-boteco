import XCTest
@testable import Models

final class WorkClockCalculatorTests: XCTestCase {
  func testEightWorkedHoursAndOneBreakHourCompletesRegularDay() {
    let entries = [
      entry(.entrada, hour: 8),
      entry(.almocoInicio, hour: 12),
      entry(.almocoFim, hour: 13),
      entry(.saida, hour: 17),
    ]
    let summary = WorkClockCalculator.summarizeDay(entries: entries)
    XCTAssertEqual(summary.workedMillis, Int64(8).hours)
    XCTAssertEqual(summary.lunchMillis, Int64(1).hours)
    XCTAssertEqual(summary.missingDailyMillis, 0)
  }

  func testOvertimeIsOnlyAboveFortyWeeklyHours() {
    let fortyHours: Int64 = 40 * 60 * 60 * 1000
    let summary = WorkClockCalculator.summarizeDay(entries: [], weeklyWorkedMillis: fortyHours + 3_600_000)
    XCTAssertEqual(summary.overtimeMillis, 1 * 60 * 60 * 1000)
  }
}

private func entry(_ type: WorkClockType, hour: Int) -> WorkClockEntry {
  WorkClockEntry(
    id: Int64(hour),
    userId: 1,
    type: type,
    registeredAt: Int64(hour).hours,
    location: WorksiteLocation.point,
    distanceFromWorkMeters: 0,
    isLate: false
  )
}

private extension Int64 {
  var hours: Int64 { self * 60 * 60 * 1000 }
}
