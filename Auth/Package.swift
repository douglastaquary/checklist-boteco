// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "Auth",
  platforms: [.iOS(.v16)],
  products: [.library(name: "Auth", targets: ["Auth"])],
  dependencies: [
    .package(path: "../Models"),
    .package(path: "../Env"),
    .package(path: "../Network"),
    .package(path: "../DesignSystem"),
    .package(path: "../Persistence"),
  ],
  targets: [
    .target(name: "Auth", dependencies: ["Models", "Env", "Network", "DesignSystem", "Persistence"]),
    .testTarget(name: "AuthTests", dependencies: ["Auth"]),
  ]
)
