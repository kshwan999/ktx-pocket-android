// swift-tools-version: 6.0
import PackageDescription
import AppleProductTypes

let package = Package(
    name: "KTXPocketTrial",
    defaultLocalization: "ko",
    platforms: [.iOS("26.0")],
    products: [
        .iOSApplication(
            name: "KTXPocketTrial",
            targets: ["AppModule"],
            bundleIdentifier: "kr.personal.ktxpocket.ios",
            displayVersion: "0.1.0",
            bundleVersion: "1",
            appIcon: .placeholder(icon: .calendar),
            accentColor: .presetColor(.orange),
            supportedDeviceFamilies: [.phone, .pad],
            supportedInterfaceOrientations: [.portrait, .landscapeLeft, .landscapeRight, .portraitUpsideDown(.when(deviceFamilies: [.pad]))],
            additionalInfoPlistContentFilePath: "AppInfo.plist"
        )
    ],
    targets: [
        .target(name: "KTXPocketCore"),
        .executableTarget(name: "AppModule", dependencies: ["KTXPocketCore"])
    ]
)
