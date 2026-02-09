// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorCommunityCameraPreview",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapacitorCommunityCameraPreviewPlugin",
            targets: ["CameraPreviewPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.2")
    ],
    targets: [
        .target(
            name: "CameraPreviewPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/CameraPreviewPlugin"),
        .testTarget(
            name: "CameraPreviewPluginTests",
            dependencies: ["CameraPreviewPlugin"],
            path: "ios/Tests/CameraPreviewPluginTests")
    ]
)
