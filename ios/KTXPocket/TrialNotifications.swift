import Foundation
import UserNotifications

enum TrialNotifications {
    static let checkInID = "ktx-trial-check-in"

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    static func scheduleCheckIn(seconds: Int) async throws {
        let content = UNMutableNotificationContent()
        content.title = "KTX 실행 시험 기록 확인"
        content.body = "예정한 시험 시간이 지났습니다. 앱을 열어 실행 간격과 중단 여부를 확인해주세요."
        content.sound = .default
        try await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: checkInID, content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: Double(seconds + 30), repeats: false)))
    }

    static func cancelCheckIn() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [checkInID])
    }

    static func show(title: String, body: String) async {
        let content = UNMutableNotificationContent(); content.title = title; content.body = body; content.sound = .default
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)))
    }
}
