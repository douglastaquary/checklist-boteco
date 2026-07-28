// swift-tools-version: 5.7
import PackageDescription

let package = Package(
  name: "Persistence",
  platforms: [.iOS(.v16)],
  products: [.library(name: "Persistence", targets: ["Persistence"])],
  dependencies: [
    .package(path: "../Models"),
    .package(url: "https://github.com/groue/GRDB.swift.git", exact: "6.29.3"),
  ],
  targets: [
    .target(name: "Persistence", dependencies: ["Models", .product(name: "GRDB", package: "GRDB.swift")]),
    .testTarget(name: "PersistenceTests", dependencies: ["Persistence"]),
  ]
)
