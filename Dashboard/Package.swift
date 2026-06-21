// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "DashboardFeature",
  platforms: [.iOS(.v16)],
  products: [.library(name: "DashboardFeature", targets: ["DashboardFeature"])],
  dependencies: [
    .package(path: "../Models"),
    .package(path: "../Persistence"),
  ],
  targets: [
    .target(name: "DashboardFeature", dependencies: ["Models", "Persistence"]),
  ]
)
