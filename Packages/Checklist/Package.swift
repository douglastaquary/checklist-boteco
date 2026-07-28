// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "ChecklistFeature",
  platforms: [.iOS(.v16)],
  products: [.library(name: "ChecklistFeature", targets: ["ChecklistFeature"])],
  dependencies: [
    .package(path: "../Models"),
    .package(path: "../Persistence"),
    .package(path: "../Env"),
    .package(path: "../DesignSystem"),
  ],
  targets: [
    .target(name: "ChecklistFeature", dependencies: ["Models", "Persistence", "Env", "DesignSystem"]),
  ]
)
