// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "AdminFeatures",
  platforms: [.iOS(.v16)],
  products: [.library(name: "AdminFeatures", targets: ["AdminFeatures"])],
  dependencies: [
    .package(path: "../Models"),
    .package(path: "../Persistence"),
  ],
  targets: [
    .target(name: "AdminFeatures", dependencies: ["Models", "Persistence"]),
  ]
)
