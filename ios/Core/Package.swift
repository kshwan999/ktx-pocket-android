// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "KTXPocketCore",
    platforms: [.macOS(.v13), .iOS("26.0")],
    products: [.library(name: "KTXPocketCore", targets: ["KTXPocketCore"])],
    targets: [
        .target(name: "KTXPocketCore"),
        .testTarget(name: "KTXPocketCoreTests", dependencies: ["KTXPocketCore"])
    ]
)
