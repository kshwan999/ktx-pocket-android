import KTXPocketCore
import SwiftUI

struct ConfirmedTrial: Identifiable {
    let id = UUID()
    let plans: [SearchPlan]
    let draft: SearchDraft
}

struct TrialConfirmation: View {
    let trial: ConfirmedTrial
    let start: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("이번 버전은 실제 열차를 조회하거나 예약하지 않습니다. 확인한 조건과 함께 아이폰의 실행 간격만 기록해요.")
                }
                ForEach(trial.plans) { plan in
                    Section {
                        ConditionLabel(condition: plan.condition)
                        if let minutes = plan.accessMinutes {
                            Text("이동 \(minutes)분 + 승차 여유 \(plan.boardingMinutes)분").font(.subheadline)
                            Text("지금 기준 \(plan.earliestDeparture(now: .now).formatted(Date.FormatStyle(date: .abbreviated, time: .shortened, locale: Locale(identifier: "ko_KR"), timeZone: TimeZone(secondsFromGMT: 32400) ?? .gmt))) 이후 출발 후보")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Section {
                    Text("시험 시간: \(trial.draft.durationMinutes)분")
                    Text("시작 후 화면을 잠그거나 다른 앱을 사용해보세요. iOS가 작업을 중단할 수 있으며, 강제 종료한 동안에는 앱이 직접 중단 알림을 보낼 수 없어요.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Button("확인하고 실행 시험 시작", action: start).buttonStyle(.borderedProminent)
                }
            }
            .navigationTitle("시험 조건 확인").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("돌아가기") { dismiss() } } }
        }
    }
}

struct ConditionLabel: View {
    let condition: SearchCondition
    private let dateStyle = Date.FormatStyle(date: .abbreviated, time: .shortened, locale: Locale(identifier: "ko_KR"),
                                             timeZone: TimeZone(secondsFromGMT: 32400) ?? .gmt)
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("\(condition.departure) → \(condition.arrival)").font(.headline)
            Text("\(condition.start.formatted(dateStyle)) ~ \(condition.end.formatted(dateStyle)) 이전")
                .font(.subheadline).foregroundStyle(.secondary)
            Text("어른 \(condition.adults)명 · \(condition.seat.title)").font(.caption).foregroundStyle(.secondary)
        }
    }
}
