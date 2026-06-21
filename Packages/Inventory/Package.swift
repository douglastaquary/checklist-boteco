// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "InventoryFeature",
  platforms: [.iOS(.v16)],
  products: [.library(name: "InventoryFeature", targets: ["InventoryFeature"])],
  dependencies: [
    .package(path: "../Models"),
    .package(path: "../Network"),
    .package(path: "../Persistence"),
    .package(path: "../DesignSystem"),
  ],
  targets: [
    .target(name: "InventoryFeature", dependencies: ["Models", "Network", "Persistence", "DesignSystem"]),
  ]
)
