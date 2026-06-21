// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "WorkClockFeature",
  platforms: [.iOS(.v16)],
  products: [.library(name: "WorkClockFeature", targets: ["WorkClockFeature"])],
  dependencies: [
    .package(path: "../Models"),
    .package(path: "../Persistence"),
    .package(path: "../Env"),
    .package(path: "../DesignSystem"),
  ],
  targets: [
    .target(name: "WorkClockFeature", dependencies: ["Models", "Persistence", "Env", "DesignSystem"]),
  ]
)
