import BackgroundTasks
import Foundation
import KTXPocketCore
import Observation
import UIKit

@MainActor @Observable
final class TrialController {
    var draft = SearchDraft()
    private(set) var record: ProbeRecord?
    private(set) var preparing = false
    private(set) var notificationAllowed = false
    var errorMessage: String?
    private(set) var storageMessage: String?
    private var visibility = AppVisibility.foreground
    @ObservationIgnored private var store: TrialStore?
    @ObservationIgnored private var systemTask: BGContinuedProcessingTask?
    @ObservationIgnored private var worker: Task<Void, Never>?
    @ObservationIgnored private var launchWatchdog: Task<Void, Never>?
    private var requestID: String?

    var busy: Bool { preparing || record?.phase.isActive == true }
    var reportURL: URL? { record != nil && !busy && storageMessage == nil ? store?.reportURL : nil }

    init() {
        do {
            let store = try TrialStore(); self.store = store
            do { draft = try store.loadDraft() }
            catch { storageMessage = "저장된 조건을 읽지 못했어요. 새 조건을 입력해주세요." }
            do {
                record = try store.loadReport()
                if record?.phase.isActive == true {
                    record?.recoverAfterRelaunch(now: .now)
                    if let record { try store.saveReport(record) }
                    TrialNotifications.cancelCheckIn()
                }
            } catch { storageMessage = "이전 시험 기록을 읽지 못했어요. 새 시험으로 확인해주세요." }
        } catch { storageMessage = "앱 내부 저장소를 준비하지 못했어요. 기록을 남길 수 없어 시험을 시작할 수 없습니다." }
    }

    func preview() throws -> [SearchPlan] {
        try PlanBuilder.make(primary: draft.primary, alternatives: draft.alternatives, access: draft.access, now: .now)
    }

    func start(plans: [SearchPlan], confirmedDraft: SearchDraft) async {
        guard !busy, let store else { return }
        preparing = true
        defer { preparing = false }
        notificationAllowed = await TrialNotifications.requestPermission()
        guard UIApplication.shared.applicationState == .active else {
            errorMessage = "앱을 화면에 연 상태에서 직접 시험을 시작해주세요."; return
        }
        do {
            storageMessage = nil
            let run = try ProbeRecord(durationSeconds: confirmedDraft.durationMinutes * 60, plans: plans, now: .now)
            try store.saveDraft(confirmedDraft); try store.saveReport(run)
            record = run
            let identifier = (Bundle.main.bundleIdentifier ?? "kr.personal.ktxpocket.ios") + ".probe." + run.id.uuidString
            requestID = identifier
            let registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { [weak self] task in
                // The callback is explicitly dispatched on the main queue; observable state stays on MainActor.
                MainActor.assumeIsolated {
                    guard let self else { task.setTaskCompleted(success: false); return }
                    self.accept(task)
                }
            }
            guard registered else { throw InputError("백그라운드 작업을 등록하지 못했어요. 앱의 작업 식별자 설정을 확인해주세요.") }
            if notificationAllowed {
                do { try await TrialNotifications.scheduleCheckIn(seconds: run.durationSeconds) }
                catch { errorMessage = "확인 알림을 예약하지 못했어요. 시험 기록은 앱에서 직접 확인해주세요." }
            }
            let request = BGContinuedProcessingTaskRequest(identifier: identifier, title: "KTX 실행 간격 시험",
                subtitle: "\(confirmedDraft.durationMinutes)분 동안 실행 간격 측정 · 실제 예약 없음")
            request.strategy = .fail
            try BGTaskScheduler.shared.submit(request)
            launchWatchdog = Task { [weak self] in
                try? await Task.sleep(for: .seconds(15))
                guard !Task.isCancelled, self?.record?.phase == .requested else { return }
                self?.finish(.failed, note: "실행 요청 후 시작 응답을 받지 못했습니다. 자동 재시도하지 않습니다.")
            }
        } catch {
            if record?.phase.isActive == true { finish(.failed, note: "백그라운드 시험을 시작하지 못했습니다. 기기 상태와 백그라운드 앱 새로 고침 설정을 확인해주세요.") }
            else { TrialNotifications.cancelCheckIn() }
            errorMessage = (error as? InputError)?.errorDescription ?? "시험을 준비하지 못했어요. 저장 공간이나 백그라운드 실행 설정을 확인해주세요."
        }
    }

    private func accept(_ task: BGTask) {
        guard let task = task as? BGContinuedProcessingTask, record?.phase == .requested,
              requestID == task.identifier else { task.setTaskCompleted(success: false); return }
        launchWatchdog?.cancel(); launchWatchdog = nil
        systemTask = task
        guard record?.start(now: .now, uptime: ProcessInfo.processInfo.systemUptime) == true else {
            task.setTaskCompleted(success: false); systemTask = nil; return
        }
        task.expirationHandler = { [weak self] in
            Task { @MainActor in self?.finish(.expired, note: "iOS에서 실행 시간을 회수했거나 시스템 화면에서 중지했습니다. 자동 재개하지 않습니다.") }
        }
        task.progress.totalUnitCount = Int64(record?.durationSeconds ?? 1)
        persistCheckpoint()
        guard record?.phase == .running else { return }
        worker = Task { [weak self] in
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(Double.random(in: 3...5))) }
                catch { return }
                guard let self, self.record?.phase == .running else { return }
                self.record?.sample(now: .now, uptime: ProcessInfo.processInfo.systemUptime, visibility: self.visibility)
                self.systemTask?.progress.completedUnitCount = Int64(min(self.record?.elapsedSeconds ?? 0,
                                                                         Double(self.record?.durationSeconds ?? 1)))
                self.persistCheckpoint()
                guard self.record?.phase == .running else { return }
                if self.record?.reachedDuration == true {
                    self.finish(.completed, note: "정한 시간 동안의 실행 간격 측정을 마쳤습니다. 네트워크 요청과 실제 예약은 실행하지 않았습니다.")
                    return
                }
            }
        }
    }

    func setVisibility(_ value: AppVisibility) { visibility = value }
    func stop() { finish(.stopped, note: "앱의 중지 버튼으로 시험을 종료했습니다.") }

    private func finish(_ phase: ProbePhase, note: String) {
        guard record?.finish(phase, now: .now, note: note) == true else { return }
        worker?.cancel(); worker = nil; launchWatchdog?.cancel(); launchWatchdog = nil
        let task = systemTask; systemTask = nil
        if let requestID { BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: requestID) }
        requestID = nil
        TrialNotifications.cancelCheckIn()
        if let store, let record {
            do { try store.saveReport(record) }
            catch { storageMessage = "마지막 시험 기록을 저장하지 못했어요. 화면에서 결과를 확인해주세요." }
        }
        task?.setTaskCompleted(success: phase == .completed)
        if phase != .stopped {
            Task { await TrialNotifications.show(title: phase.title, body: note) }
        }
    }

    private func persistCheckpoint() {
        guard let store, let record else { return }
        do { try store.saveReport(record) }
        catch {
            storageMessage = "실행 기록을 저장하지 못해 시험을 중지했어요. 저장 공간을 확인해주세요."
            finish(.failed, note: "기록 저장에 실패해 실행 시험을 중지했습니다.")
        }
    }

    func clearRecord() {
        guard !busy else { return }
        do { try store?.clearReport(); record = nil; storageMessage = nil }
        catch { errorMessage = "시험 기록을 지우지 못했어요." }
    }
}
