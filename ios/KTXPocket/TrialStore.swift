import Foundation
import KTXPocketCore

struct SearchDraft: Codable {
    var primary = SearchCondition()
    var alternatives: [SearchCondition] = []
    var access = AccessSettings()
    var durationMinutes = 5
}

@MainActor
final class TrialStore {
    private let directory: URL
    let reportURL: URL
    private let draftURL: URL

    init() throws {
        directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                                appropriateFor: nil, create: true).appendingPathComponent("KTXTrial", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        var folder = directory; try folder.setResourceValues(values)
        reportURL = directory.appendingPathComponent("실행시험-최근기록.json")
        draftURL = directory.appendingPathComponent("conditions.json")
    }

    func loadDraft() throws -> SearchDraft {
        guard FileManager.default.fileExists(atPath: draftURL.path) else { return SearchDraft() }
        return try JSONDecoder().decode(SearchDraft.self, from: Data(contentsOf: draftURL))
    }

    func loadReport() throws -> ProbeRecord? {
        guard FileManager.default.fileExists(atPath: reportURL.path) else { return nil }
        return try JSONDecoder().decode(ProbeRecord.self, from: Data(contentsOf: reportURL))
    }

    func saveDraft(_ draft: SearchDraft) throws { try save(draft, to: draftURL) }
    func saveReport(_ report: ProbeRecord) throws { try save(report, to: reportURL) }
    func clearReport() throws {
        if FileManager.default.fileExists(atPath: reportURL.path) { try FileManager.default.removeItem(at: reportURL) }
    }

    private func save<T: Encodable>(_ value: T, to url: URL) throws {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        // Keep checkpoint writes available after the user locks an already-unlocked phone.
        try encoder.encode(value).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
