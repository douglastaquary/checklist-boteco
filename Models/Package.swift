// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "Models",
  platforms: [.iOS(.v16)],
  products: [.library(name: "Models", targets: ["Models"])],
  targets: [
    .target(name: "Models"),
    .testTarget(name: "ModelsTests", dependencies: ["Models"]),
  ]
)
