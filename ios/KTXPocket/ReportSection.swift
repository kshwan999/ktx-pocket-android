import KTXPocketCore
import SwiftUI

struct ReportSection: View {
    let record: ProbeRecord
    let reportURL: URL?

    var body: some View {
        Section("최근 실행 기록") {
            LabeledContent("목표 시간", value: "\(record.durationSeconds / 60)분")
            LabeledContent("측정된 경과 시간", value: "\(record.elapsedSeconds.formatted(.number.precision(.fractionLength(1))))초")
            LabeledContent("가장 긴 실행 간격", value: "\(record.maxGapSeconds.formatted(.number.precision(.fractionLength(1))))초")
            LabeledContent("10초 초과 간격", value: "\(record.delayedSamples)회")
            LabeledContent("화면 밖 실행 표본", value: "\(record.backgroundSamples)개")
            LabeledContent("외부 통신 요청", value: "0회")
            if record.phase == .requested || record.phase == .running {
                Text("진행 중인 값이에요. 측정이 끝나면 기록을 공유할 수 있습니다.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            DisclosureGroup("마지막 실행 시각 보기") {
                ForEach(Array(record.samples.suffix(12))) { sample in
                    VStack(alignment: .leading) {
                        Text(sample.timestamp, format: .dateTime.hour().minute().second())
                            .font(.subheadline.monospacedDigit())
                        Text("\(visibility(sample.visibility)) · 간격 \(sample.gapSeconds.formatted(.number.precision(.fractionLength(1))))초")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            if let reportURL {
                ShareLink("시험 기록 공유", item: reportURL, preview: SharePreview("KTX 실행 시험 기록"))
                Text("공유 파일에는 입력한 여행 조건과 실행 시각이 들어 있어요. 공유 대상을 직접 선택해주세요.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func visibility(_ value: AppVisibility) -> String {
        switch value {
        case .foreground: "앱 화면에서 실행"
        case .inactive: "화면 전환 중 실행"
        case .background: "화면 밖에서 실행"
        }
    }
}
