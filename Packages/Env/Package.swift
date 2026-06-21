// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "Env",
  platforms: [.iOS(.v16)],
  products: [.library(name: "Env", targets: ["Env"])],
  dependencies: [
    .package(path: "../Models"),
    .package(path: "../Network"),
    .package(path: "../Persistence"),
  ],
  targets: [
    .target(name: "Env", dependencies: ["Models", "Network", "Persistence"]),
    .testTarget(name: "EnvTests", dependencies: ["Env"]),
  ]
)
