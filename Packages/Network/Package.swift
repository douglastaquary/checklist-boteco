// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "Network",
  platforms: [.iOS(.v16)],
  products: [.library(name: "Network", targets: ["Network"])],
  dependencies: [
    .package(path: "../Models"),
  ],
  targets: [
    .target(name: "Network", dependencies: ["Models"]),
    .testTarget(name: "NetworkTests", dependencies: ["Network"]),
  ]
)
