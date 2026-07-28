import SwiftUI

/// Year-to-date contribution-style sales heatmap (daily cells, Jan 1 → today).
public struct BecoSalesHeatmap: View {
  public let title: String
  public let quantitiesByDay: [Date: Double]
  public let revenueCentsByDay: [Date: Int64]
  public let year: Int
  public let isDark: Bool

  private let calendar: Calendar
  private let cellSize: CGFloat = 11
  private let cellSpacing: CGFloat = 3

  public init(
    title: String,
    quantitiesByDay: [Date: Double],
    revenueCentsByDay: [Date: Int64] = [:],
    year: Int,
    isDark: Bool,
    calendar: Calendar = .current
  ) {
    self.title = title
    self.quantitiesByDay = quantitiesByDay
    self.revenueCentsByDay = revenueCentsByDay
    self.year = year
    self.isDark = isDark
    self.calendar = calendar
  }

  private var maxQuantity: Double {
    quantitiesByDay.values.max() ?? 0
  }

  private var weeks: [[Date?]] {
    Self.buildWeeks(year: year, calendar: calendar, through: Date())
  }

  private var monthMarkers: [MonthMarker?] {
    Self.monthMarkers(
      for: weeks,
      calendar: calendar,
      revenueCentsByDay: revenueCentsByDay
    )
  }

  private var mutedLabel: Color {
    isDark ? Color.white.opacity(0.45) : Color.secondary.opacity(0.85)
  }

  public var body: some View {
    VStack(alignment: .leading, spacing: 10) {
      Text(title)
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(isDark ? Color.white.opacity(0.62) : Color.secondary)

      ScrollView(.horizontal, showsIndicators: false) {
        VStack(alignment: .leading, spacing: 6) {
          HStack(alignment: .top, spacing: cellSpacing) {
            ForEach(Array(weeks.enumerated()), id: \.offset) { _, week in
              VStack(spacing: cellSpacing) {
                ForEach(0..<7, id: \.self) { dayIndex in
                  RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(fill(for: week.indices.contains(dayIndex) ? week[dayIndex] : nil))
                    .frame(width: cellSize, height: cellSize)
                }
              }
            }
          }

          HStack(alignment: .top, spacing: 0) {
            ForEach(Array(monthMarkers.enumerated()), id: \.offset) { _, marker in
              if let marker {
                VStack(alignment: .leading, spacing: 1) {
                  Text(marker.letter)
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(mutedLabel)
                  Text(Self.formatRevenue(cents: marker.totalCents))
                    .font(.system(size: 8, weight: .regular))
                    .foregroundStyle(mutedLabel)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                }
                .frame(
                  width: max(
                    cellSize,
                    CGFloat(marker.weekCount) * cellSize
                      + CGFloat(max(0, marker.weekCount - 1)) * cellSpacing
                  ),
                  alignment: .leading
                )
              }
            }
          }
        }
      }
      .padding(12)
      .background(
        (isDark ? Color.white.opacity(0.08) : Color(.secondarySystemGroupedBackground)),
        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
      )
    }
  }

  private func fill(for date: Date?) -> Color {
    guard let date else {
      return Color.clear
    }
    let quantity = quantitiesByDay[calendar.startOfDay(for: date)] ?? 0
    return Self.intensityColor(quantity: quantity, maxQuantity: maxQuantity, isDark: isDark)
  }

  static func intensityColor(quantity: Double, maxQuantity: Double, isDark: Bool) -> Color {
    guard quantity > 0, maxQuantity > 0 else {
      return isDark ? Color.white.opacity(0.08) : Color.black.opacity(0.06)
    }
    let ratio = min(1, quantity / maxQuantity)
    if isDark {
      return Color.white.opacity(0.18 + ratio * 0.82)
    }
    return Color.black.opacity(0.12 + ratio * 0.78)
  }

  /// Compact BRL for month footers (e.g. `R$ 1,2 mil`).
  static func formatRevenue(cents: Int64) -> String {
    let reais = Double(cents) / 100.0
    if reais <= 0 { return "—" }
    if reais < 1000 {
      let value = reais.rounded()
      return "R$ \(Int(value))"
    }
    if reais < 1_000_000 {
      let mil = reais / 1000.0
      return "R$ \(compactNumber(mil)) mil"
    }
    let mi = reais / 1_000_000.0
    return "R$ \(compactNumber(mi)) mi"
  }

  private static func compactNumber(_ value: Double) -> String {
    let rounded = (value * 10).rounded() / 10
    if abs(rounded.rounded() - rounded) < 0.05 {
      return "\(Int(rounded.rounded()))"
    }
    return String(format: "%.1f", rounded).replacingOccurrences(of: ".", with: ",")
  }

  static func monthMarkers(
    for weeks: [[Date?]],
    calendar: Calendar,
    revenueCentsByDay: [Date: Int64]
  ) -> [MonthMarker?] {
    var markers: [MonthMarker] = []
    var lastMonth: Int?
    var currentLetter = ""
    var currentWeekCount = 0
    var currentCents: Int64 = 0
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "pt_BR")
    formatter.calendar = calendar
    formatter.dateFormat = "LLL"

    func flush() {
      guard currentWeekCount > 0 else { return }
      markers.append(
        MonthMarker(letter: currentLetter, weekCount: currentWeekCount, totalCents: currentCents)
      )
      currentWeekCount = 0
    }

    for week in weeks {
      guard let date = week.compactMap({ $0 }).first else {
        if currentWeekCount > 0 { currentWeekCount += 1 }
        continue
      }
      let month = calendar.component(.month, from: date)
      if month != lastMonth {
        flush()
        lastMonth = month
        let abbreviated = formatter.string(from: date)
        currentLetter = abbreviated.first.map { String($0).uppercased() } ?? "?"
        currentWeekCount = 1
        currentCents = monthlyTotal(
          month: month,
          year: calendar.component(.year, from: date),
          calendar: calendar,
          revenueCentsByDay: revenueCentsByDay
        )
      } else {
        currentWeekCount += 1
      }
    }
    flush()
    return markers.map { Optional($0) }
  }

  private static func monthlyTotal(
    month: Int,
    year: Int,
    calendar: Calendar,
    revenueCentsByDay: [Date: Int64]
  ) -> Int64 {
    revenueCentsByDay.reduce(into: Int64(0)) { partial, entry in
      let components = calendar.dateComponents([.year, .month], from: entry.key)
      if components.year == year, components.month == month {
        partial += entry.value
      }
    }
  }

  /// Weeks as columns; each column has 7 slots (Sun…Sat).
  static func buildWeeks(year: Int, calendar: Calendar, through endDate: Date) -> [[Date?]] {
    var cal = calendar
    cal.firstWeekday = 1

    guard
      let yearStart = cal.date(from: DateComponents(year: year, month: 1, day: 1))
    else { return [] }

    let today = cal.startOfDay(for: endDate)
    let yearEndCandidate = cal.date(from: DateComponents(year: year, month: 12, day: 31)) ?? today
    let rangeEnd = min(today, cal.startOfDay(for: yearEndCandidate))
    guard rangeEnd >= cal.startOfDay(for: yearStart) else { return [] }

    let weekdayOfStart = cal.component(.weekday, from: yearStart)
    let leadingBlanks = (weekdayOfStart - cal.firstWeekday + 7) % 7

    var days: [Date?] = Array(repeating: nil, count: leadingBlanks)
    var cursor = cal.startOfDay(for: yearStart)
    while cursor <= rangeEnd {
      days.append(cursor)
      guard let next = cal.date(byAdding: .day, value: 1, to: cursor) else { break }
      cursor = next
    }

    var result: [[Date?]] = []
    var week: [Date?] = []
    for day in days {
      week.append(day)
      if week.count == 7 {
        result.append(week)
        week = []
      }
    }
    if !week.isEmpty {
      while week.count < 7 { week.append(nil) }
      result.append(week)
    }
    return result
  }
}

struct MonthMarker: Hashable {
  let letter: String
  let weekCount: Int
  let totalCents: Int64
}
