// swift-tools-version: 5.7
import PackageDescription

/// Package umbrella — referenciado pelo `ChecklistBoteco.xcodeproj` (padrão WIMB / Xcode 14.2).
let package = Package(
  name: "ChecklistBotecoPackages",
  defaultLocalization: "pt-BR",
  platforms: [.iOS(.v16)],
  products: [
    .library(name: "Models", targets: ["Models"]),
    .library(name: "Network", targets: ["Network"]),
    .library(name: "Persistence", targets: ["Persistence"]),
    .library(name: "Env", targets: ["Env"]),
    .library(name: "DesignSystem", targets: ["DesignSystem"]),
    .library(name: "Auth", targets: ["Auth"]),
    .library(name: "ChecklistFeature", targets: ["ChecklistFeature"]),
    .library(name: "WorkClockFeature", targets: ["WorkClockFeature"]),
    .library(name: "InventoryFeature", targets: ["InventoryFeature"]),
    .library(name: "DashboardFeature", targets: ["DashboardFeature"]),
    .library(name: "AdminFeatures", targets: ["AdminFeatures"]),
    .library(name: "AIChatFeature", targets: ["AIChatFeature"]),
    .library(name: "PurchasesFeature", targets: ["PurchasesFeature"]),
  ],
  dependencies: [
    .package(url: "https://github.com/groue/GRDB.swift.git", exact: "6.29.3"),
  ],
  targets: [
    .target(name: "Models", path: "Models/Sources/Models"),
    .target(name: "Network", dependencies: ["Models"], path: "Network/Sources/Network"),
    .target(
      name: "Persistence",
      dependencies: ["Models", .product(name: "GRDB", package: "GRDB.swift")],
      path: "Persistence/Sources/Persistence"
    ),
    .target(
      name: "Env",
      dependencies: ["Models", "Network", "Persistence"],
      path: "Env/Sources/Env"
    ),
    .target(name: "DesignSystem", dependencies: ["Models", "Network"], path: "DesignSystem/Sources/DesignSystem"),
    .target(
      name: "Auth",
      dependencies: ["Models", "Env", "Network", "DesignSystem", "Persistence"],
      path: "Auth/Sources/Auth"
    ),
    .target(
      name: "ChecklistFeature",
      dependencies: ["Models", "Persistence", "Env", "DesignSystem"],
      path: "Checklist/Sources/ChecklistFeature"
    ),
    .target(
      name: "WorkClockFeature",
      dependencies: ["Models", "Persistence", "Env", "DesignSystem", "Network"],
      path: "WorkClock/Sources/WorkClockFeature"
    ),
    .target(
      name: "InventoryFeature",
      dependencies: ["Models", "Network", "Persistence", "DesignSystem"],
      path: "Inventory/Sources/InventoryFeature"
    ),
    .target(
      name: "PurchasesFeature",
      dependencies: ["Models", "Network", "DesignSystem"],
      path: "Purchases/Sources/PurchasesFeature"
    ),
    .target(
      name: "DashboardFeature",
      dependencies: ["Models", "Network", "Persistence", "DesignSystem"],
      path: "Dashboard/Sources/DashboardFeature"
    ),
    .target(
      name: "AdminFeatures",
      dependencies: ["Models", "Network", "Persistence", "DesignSystem"],
      path: "AdminFeatures/Sources/AdminFeatures"
    ),
    .target(
      name: "AIChatFeature",
      dependencies: ["Network", "DesignSystem"],
      path: "AIChat/Sources/AIChatFeature"
    ),
    .testTarget(name: "ModelsTests", dependencies: ["Models"], path: "Models/Tests/ModelsTests"),
    .testTarget(name: "NetworkTests", dependencies: ["Network"], path: "Network/Tests/NetworkTests"),
    .testTarget(name: "PersistenceTests", dependencies: ["Persistence"], path: "Persistence/Tests/PersistenceTests"),
    .testTarget(name: "AuthTests", dependencies: ["Auth"], path: "Auth/Tests/AuthTests"),
    .testTarget(name: "EnvTests", dependencies: ["Env"], path: "Env/Tests/EnvTests"),
    .testTarget(name: "PurchasesFeatureTests", dependencies: ["PurchasesFeature"], path: "Purchases/Tests/PurchasesFeatureTests"),
  ]
)
