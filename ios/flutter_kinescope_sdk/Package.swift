// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "flutter_kinescope_sdk",
    platforms: [
        .iOS("15.0"),
    ],
    products: [
        .library(name: "flutter-kinescope-sdk", targets: ["flutter_kinescope_sdk"]),
    ],
    dependencies: [
        .package(name: "FlutterFramework", path: "../FlutterFramework"),
        .package(url: "https://github.com/kinescope/ios-kinescope-player.git", exact: "1.0.2"),
    ],
    targets: [
        .target(
            name: "flutter_kinescope_sdk",
            dependencies: [
                .product(name: "FlutterFramework", package: "FlutterFramework"),
                .product(name: "KinescopeSDK", package: "ios-kinescope-player"),
            ]
        ),
    ]
)
