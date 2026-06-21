// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "DesignSystem",
  platforms: [.iOS(.v16)],
  products: [.library(name: "DesignSystem", targets: ["DesignSystem"])],
  dependencies: [
    .package(path: "../Network"),
  ],
  targets: [
    .target(name: "DesignSystem", dependencies: ["Network"]),
  ]
)
