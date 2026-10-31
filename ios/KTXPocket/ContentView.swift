import KTXPocketCore
import SwiftUI

struct ContentView: View {
    @Bindable var controller: TrialController
    @Environment(\.scenePhase) private var scenePhase
    @State private var preview: ConfirmedTrial?
    @State private var showAlternatives = false

    private let kst = TimeZone(secondsFromGMT: 9 * 3600) ?? .gmt

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label("아이폰 실행 시험 버전", systemImage: "testtube.2")
                        .font(.headline).foregroundStyle(.tint)
                    Text("화면을 끄거나 다른 앱을 쓰는 동안 실행이 유지되는지 확인해요. 실제 열차 조회·예약은 아직 연결되지 않았습니다.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                statusSection
                Section("어디로 떠나세요?") {
                    HStack {
                        TextField("출발역", text: $controller.draft.primary.departure)
                            .accessibilityLabel("출발역")
                        Button("역 바꾸기", systemImage: "arrow.left.arrow.right") {
                            let previous = controller.draft.primary.departure
                            controller.draft.primary.departure = controller.draft.primary.arrival
                            controller.draft.primary.arrival = previous
                        }.labelStyle(.iconOnly).buttonStyle(.borderless)
                        TextField("도착역", text: $controller.draft.primary.arrival)
                            .multilineTextAlignment(.trailing).accessibilityLabel("도착역")
                    }
                    DatePicker("출발 시작", selection: $controller.draft.primary.start)
                    DatePicker("출발 종료", selection: $controller.draft.primary.end)
                    Stepper("어른 \(controller.draft.primary.adults)명", value: $controller.draft.primary.adults, in: 1...6)
                    Picker("좌석", selection: $controller.draft.primary.seat) {
                        ForEach(SeatPreference.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Text("출발 종료 시각 이전의 열차를 대상으로 조건을 계산해요. 한국시간 기준입니다.")
                        .font(.caption).foregroundStyle(.secondary)
                }.disabled(controller.busy)
                accessSection.disabled(controller.busy)
                alternativesSection.disabled(controller.busy)
                Section("실행 시험") {
                    Picker("측정 시간", selection: $controller.draft.durationMinutes) {
                        ForEach([1, 5, 15, 30], id: \.self) { Text("\($0)분").tag($0) }
                    }.disabled(controller.busy)
                    Text("3~5초 사이의 간격으로 실행 시각을 기록합니다. 측정 중 홈 화면으로 나가거나 화면을 잠근 뒤 돌아와 결과를 확인하세요.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Text("시험 완료는 예약 성공을 뜻하지 않아요. 통신 부하·저전력 모드·발열 등에 따라 실제 검색 결과는 달라질 수 있습니다.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let record = controller.record { ReportSection(record: record, reportURL: controller.reportURL) }
                Section {
                    if let storageMessage = controller.storageMessage {
                        Label(storageMessage, systemImage: "exclamationmark.triangle").foregroundStyle(.orange)
                    }
                    Button("최근 시험 기록 지우기", role: .destructive) { controller.clearRecord() }
                        .disabled(controller.busy || controller.record == nil)
                    Text("조건과 최근 시험 기록만 이 아이폰에 저장합니다. 계정·비밀번호를 받지 않으며 외부 서버로 전송하지 않아요.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("KTX 자리찾기")
            .environment(\.timeZone, kst)
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom) { actions }
            .sheet(item: $preview) { value in
                TrialConfirmation(trial: value) {
                    preview = nil
                    Task { await controller.start(plans: value.plans, confirmedDraft: value.draft) }
                }
            }
            .alert("확인이 필요해요", isPresented: Binding(get: { controller.errorMessage != nil }, set: { if !$0 { controller.errorMessage = nil } })) {
                Button("확인") { controller.errorMessage = nil }
            } message: { Text(controller.errorMessage ?? "") }
            .onChange(of: scenePhase, initial: true) { _, phase in
                switch phase {
                case .active: controller.setVisibility(.foreground)
                case .background: controller.setVisibility(.background)
                default: controller.setVisibility(.inactive)
                }
            }
        }
        .tint(Color(red: 0.73, green: 0.29, blue: 0.17))
    }

    private var statusSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                Text(controller.record?.phase.title ?? "조건을 정하고 실행을 시험해보세요").font(.title2.bold())
                if let record = controller.record {
                    ProgressView(value: record.progress).accessibilityLabel("실행 간격 측정 진행률")
                    Text("\(record.samples.count)개 표본 · 화면 밖 \(record.backgroundSamples)개")
                        .font(.subheadline.monospacedDigit())
                    Text(record.assessment).font(.subheadline).foregroundStyle(.secondary)
                    if record.phase.isActive && !controller.notificationAllowed {
                        Text("결과 알림이 허용되지 않았어요. 시험 뒤 앱을 열어 기록을 확인해주세요.")
                            .font(.caption).foregroundStyle(.orange)
                    }
                    if !record.phase.isActive { Text(record.note).font(.caption).foregroundStyle(.secondary) }
                }
            }.padding(.vertical, 6)
        }
    }

    private var accessSection: some View {
        Section {
            DisclosureGroup("다른 출발역도 찾아보기") {
                Toggle("이동 시간으로 대안 계산", isOn: $controller.draft.access.enabled)
                Text("같은 출발지에서 각 역까지 걸리는 시간을 직접 입력하세요. 현재 출발역도 등록해야 해요.")
                    .font(.caption).foregroundStyle(.secondary)
                ForEach($controller.draft.access.stations) { $station in
                    VStack(alignment: .leading) {
                        TextField("역 이름", text: $station.name)
                            .onChange(of: station.name) { _, _ in station.minutes = nil }
                            .accessibilityLabel("이동 시간을 등록할 역 이름")
                        TextField("이동 시간 (분)", value: $station.minutes, format: .number.grouping(.never))
                            .keyboardType(.numberPad).accessibilityLabel("\(station.name)역까지 이동 시간, 분")
                    }
                }
                Stepper("추가 이동 허용 \(controller.draft.access.extraMinutes)분", value: $controller.draft.access.extraMinutes, in: 0...360, step: 5)
                Stepper("승차 여유 \(controller.draft.access.boardingMinutes)분", value: $controller.draft.access.boardingMinutes, in: 0...120, step: 5)
                Text("직접 추가한 조건을 우선하고, 남는 자리에 이동 시간이 짧은 역을 넣어요. 전체 최대 3개이며 지도·실시간 교통은 계산하지 않습니다.")
                    .font(.caption).foregroundStyle(.secondary)
                Button("역 이동 시간 초기화", role: .destructive) {
                    controller.draft.access = AccessSettings()
                    controller.draft.access.stations[0].name = controller.draft.primary.departure
                }
            }
        }
    }

    private var alternativesSection: some View {
        Section {
            DisclosureGroup("다른 시간도 괜찮다면", isExpanded: $showAlternatives) {
                ForEach(controller.draft.alternatives) { condition in
                    VStack(alignment: .leading, spacing: 8) {
                        ConditionLabel(condition: condition)
                        Button("이 대안 삭제", role: .destructive) { controller.draft.alternatives.removeAll { $0.id == condition.id } }
                    }
                }
                Button("지금 조건을 대안으로 추가", systemImage: "plus") {
                    do {
                        var condition = try controller.draft.primary.validated(now: .now)
                        guard !controller.draft.alternatives.contains(where: { $0.hasSameCriteria(as: condition) }) else {
                            throw InputError("이미 추가한 조건이에요.")
                        }
                        condition.id = UUID(); controller.draft.alternatives.append(condition)
                    } catch { controller.errorMessage = error.localizedDescription }
                }.disabled(controller.draft.alternatives.count >= 2)
                Text("추가한 뒤 위 입력란을 다른 날짜·시간으로 바꾸세요. 출발역 자동 대안까지 합쳐 최대 3개를 확인합니다.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var actions: some View {
        VStack(spacing: 8) {
            Text("실행 시험 전용 · 실제 조회·예약 없음").font(.caption).foregroundStyle(.secondary)
            if controller.preparing {
                ProgressView("알림 권한과 실행 준비 중")
            } else if controller.busy {
                Button("실행 시험 중지", role: .destructive) { controller.stop() }
                    .buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
            } else {
                Button("조건 확인하고 실행 시험") {
                    do { preview = ConfirmedTrial(plans: try controller.preview(), draft: controller.draft) }
                    catch { controller.errorMessage = error.localizedDescription }
                }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
            }
        }.controlSize(.large).padding().frame(maxWidth: .infinity).background(.regularMaterial)
    }
}
