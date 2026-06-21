#!/usr/bin/env python3
"""Gera ChecklistBoteco.xcodeproj — app + package umbrella local (padrão WIMB / Xcode 14.2)."""
import os
import uuid

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(ROOT, ".."))
PACKAGES_DIR = os.path.join(REPO, "Packages")
PROJECT_DIR = os.path.join(ROOT, "ChecklistBoteco.xcodeproj")

APP_SOURCES = [
  "ChecklistBoteco/ChecklistBotecoApp.swift",
  "ChecklistBoteco/MainTabView.swift",
  "ChecklistBoteco/AppDependencies.swift",
]

APP_PRODUCTS = [
  "Models", "Network", "Persistence", "Env", "DesignSystem", "Auth",
  "ChecklistFeature", "WorkClockFeature", "InventoryFeature",
  "DashboardFeature", "AdminFeatures",
]


def uid():
  return uuid.uuid4().hex[:24].upper()


project_uid = uid()
target_uid = uid()
sources_phase = uid()
frameworks_phase = uid()
resources_phase = uid()
debug_cfg = uid()
release_cfg = uid()
target_config_list = uid()
project_config_list = uid()
group_root = uid()
group_products = uid()
group_app = uid()
group_config = uid()
product_ref = uid()
info_plist = uid()
debug_xcconfig = uid()
release_xcconfig = uid()
packages_ref = uid()
product_deps = {name: uid() for name in APP_PRODUCTS}
framework_build_files = {name: uid() for name in APP_PRODUCTS}

file_refs = {}
build_files = []
for src in APP_SOURCES:
  ref = uid()
  bf = uid()
  file_refs[src] = ref
  build_files.append((bf, ref))

packages_url = "file://" + PACKAGES_DIR

pbx = f"""// !$*UTF8*$!
{{
  archiveVersion = 1;
  classes = {{}};
  objectVersion = 56;
  objects = {{

/* Begin PBXBuildFile section */
"""
for bf, ref in build_files:
  pbx += f"\t\t{bf} /* file */ = {{isa = PBXBuildFile; fileRef = {ref}; }};\n"
for name in APP_PRODUCTS:
  pbx += (
    f"\t\t{framework_build_files[name]} /* {name} in Frameworks */ = {{isa = PBXBuildFile; "
    f"productRef = {product_deps[name]} /* {name} */; }};\n"
  )
pbx += "/* End PBXBuildFile section */\n\n"

pbx += "/* Begin PBXFileReference section */\n"
for src, ref in file_refs.items():
  name = os.path.basename(src)
  pbx += (
    f"\t\t{ref} /* {name} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; "
    f"path = {name}; sourceTree = \"<group>\"; }};\n"
  )
pbx += (
  f"\t\t{product_ref} /* ChecklistBoteco.app */ = {{isa = PBXFileReference; "
  f"explicitFileType = wrapper.application; includeInIndex = 0; path = ChecklistBoteco.app; "
  f"sourceTree = BUILT_PRODUCTS_DIR; }};\n"
)
pbx += (
  f"\t\t{info_plist} /* Info.plist */ = {{isa = PBXFileReference; lastKnownFileType = text.plist.xml; "
  f"path = Info.plist; sourceTree = \"<group>\"; }};\n"
)
pbx += (
  f"\t\t{debug_xcconfig} /* Debug.xcconfig */ = {{isa = PBXFileReference; lastKnownFileType = text.xcconfig; "
  f"path = Debug.xcconfig; sourceTree = \"<group>\"; }};\n"
)
pbx += (
  f"\t\t{release_xcconfig} /* Release.xcconfig */ = {{isa = PBXFileReference; lastKnownFileType = text.xcconfig; "
  f"path = Release.xcconfig; sourceTree = \"<group>\"; }};\n"
)
pbx += "/* End PBXFileReference section */\n\n"

framework_files = ", ".join(framework_build_files[n] for n in APP_PRODUCTS)
pbx += "/* Begin PBXFrameworksBuildPhase section */\n"
pbx += (
  f"\t\t{frameworks_phase} /* Frameworks */ = {{isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; "
  f"files = ({framework_files}); runOnlyForDeploymentPostprocessing = 0; }};\n"
)
pbx += "/* End PBXFrameworksBuildPhase section */\n\n"

children = ", ".join(file_refs[s] for s in APP_SOURCES) + f", {info_plist}"
pbx += "/* Begin PBXGroup section */\n"
pbx += (
  f"\t\t{group_root} = {{isa = PBXGroup; children = ({group_app}, {group_config}, {group_products}); "
  f"sourceTree = \"<group>\"; }};\n"
)
pbx += (
  f"\t\t{group_products} /* Products */ = {{isa = PBXGroup; children = ({product_ref}); "
  f"name = Products; sourceTree = \"<group>\"; }};\n"
)
pbx += (
  f"\t\t{group_config} /* Config */ = {{isa = PBXGroup; children = ({debug_xcconfig}, {release_xcconfig}); "
  f"path = Config; sourceTree = \"<group>\"; }};\n"
)
pbx += (
  f"\t\t{group_app} /* ChecklistBoteco */ = {{isa = PBXGroup; children = ({children}); "
  f"path = ChecklistBoteco; sourceTree = \"<group>\"; }};\n"
)
pbx += "/* End PBXGroup section */\n\n"

deps_list = ", ".join(product_deps[n] for n in APP_PRODUCTS)
pbx += "/* Begin PBXNativeTarget section */\n"
pbx += (
  f"\t\t{target_uid} /* ChecklistBoteco */ = {{isa = PBXNativeTarget; buildConfigurationList = {target_config_list}; "
  f"buildPhases = ({sources_phase}, {frameworks_phase}, {resources_phase}); buildRules = (); dependencies = (); "
  f"name = ChecklistBoteco; packageProductDependencies = ({deps_list}); productName = ChecklistBoteco; "
  f"productReference = {product_ref}; productType = \"com.apple.product-type.application\"; }};\n"
)
pbx += "/* End PBXNativeTarget section */\n\n"

pbx += "/* Begin PBXProject section */\n"
pbx += (
  f"\t\t{project_uid} /* Project object */ = {{isa = PBXProject; attributes = {{LastSwiftUpdateCheck = 1420; "
  f"LastUpgradeCheck = 1420;}}; buildConfigurationList = {project_config_list}; compatibilityVersion = \"Xcode 14.0\"; "
  f"developmentRegion = pt-BR; hasScannedForEncodings = 0; knownRegions = (pt-BR, en); mainGroup = {group_root}; "
  f"packageReferences = ({packages_ref}); productRefGroup = {group_products}; projectDirPath = \"\"; "
  f"projectRoot = \"\"; targets = ({target_uid}); }};\n"
)
pbx += "/* End PBXProject section */\n\n"

pbx += "/* Begin PBXResourcesBuildPhase section */\n"
pbx += (
  f"\t\t{resources_phase} /* Resources */ = {{isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; "
  f"files = (); runOnlyForDeploymentPostprocessing = 0; }};\n"
)
pbx += "/* End PBXResourcesBuildPhase section */\n\n"

files_list = ", ".join(bf for bf, _ in build_files)
pbx += "/* Begin PBXSourcesBuildPhase section */\n"
pbx += (
  f"\t\t{sources_phase} /* Sources */ = {{isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; "
  f"files = ({files_list}); runOnlyForDeploymentPostprocessing = 0; }};\n"
)
pbx += "/* End PBXSourcesBuildPhase section */\n\n"

pbx += "/* Begin XCBuildConfiguration section */\n"
for cfg_uid, name, xc_ref in [
  (debug_cfg, "Debug", debug_xcconfig),
  (release_cfg, "Release", release_xcconfig),
]:
  pbx += (
    f"\t\t{cfg_uid} /* {name} */ = {{isa = XCBuildConfiguration; baseConfigurationReference = {xc_ref}; "
    f"buildSettings = {{ALWAYS_SEARCH_USER_PATHS = NO; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; "
    f"GENERATE_INFOPLIST_FILE = NO; INFOPLIST_FILE = ChecklistBoteco/Info.plist; IPHONEOS_DEPLOYMENT_TARGET = 16.0; "
    f"LD_RUNPATH_SEARCH_PATHS = (\"$(inherited)\", \"@executable_path/Frameworks\"); MARKETING_VERSION = 1.0; "
    f"ONLY_ACTIVE_ARCH = YES; PRODUCT_BUNDLE_IDENTIFIER = com.checklistboteco.ios; PRODUCT_NAME = ChecklistBoteco; "
    f"SDKROOT = iphoneos; SUPPORTED_PLATFORMS = \"iphoneos iphonesimulator\"; SWIFT_VERSION = 5.0; "
    f"TARGETED_DEVICE_FAMILY = \"1,2\"; }}; name = {name}; }};\n"
  )
pbx += (
  f"\t\t{target_config_list} = {{isa = XCConfigurationList; buildConfigurations = ({debug_cfg}, {release_cfg}); "
  f"defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};\n"
)
pbx += (
  f"\t\t{project_config_list} = {{isa = XCConfigurationList; buildConfigurations = ({debug_cfg}, {release_cfg}); "
  f"defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};\n"
)
pbx += "/* End XCBuildConfiguration section */\n\n"

pbx += "/* Begin XCRemoteSwiftPackageReference section */\n"
pbx += (
  f"\t\t{packages_ref} /* XCRemoteSwiftPackageReference Packages */ = {{isa = XCRemoteSwiftPackageReference; "
  f"repositoryURL = \"{packages_url}\"; requirement = {{kind = branch; branch = main;}}; }};\n"
)
pbx += "/* End XCRemoteSwiftPackageReference section */\n\n"

pbx += "/* Begin XCSwiftPackageProductDependency section */\n"
for name in APP_PRODUCTS:
  pbx += (
    f"\t\t{product_deps[name]} /* {name} */ = {{isa = XCSwiftPackageProductDependency; productName = {name}; "
    f"package = {packages_ref} /* XCRemoteSwiftPackageReference Packages */; }};\n"
  )
pbx += "/* End XCSwiftPackageProductDependency section */\n\n"

pbx += "  };\n"
pbx += f"  rootObject = {project_uid} /* Project object */;\n"
pbx += "}\n"

scheme_dir = os.path.join(PROJECT_DIR, "xcshareddata", "xcschemes")
os.makedirs(scheme_dir, exist_ok=True)
scheme = f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1420" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">
    <BuildActionEntries>
      <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES"
        buildForArchiving="YES" buildForAnalyzing="YES">
        <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target_uid}"
          BuildableName="ChecklistBoteco.app" BlueprintName="ChecklistBoteco"
          ReferencedContainer="container:ChecklistBoteco.xcodeproj"/>
      </BuildActionEntry>
    </BuildActionEntries>
  </BuildAction>
  <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB"
    selectedLauncherIdentifier="Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle="0"
    useCustomWorkingDirectory="NO" debugDocumentVersioning="YES" debugServiceExtension="internal"
    allowLocationSimulation="YES">
    <BuildableProductRunnable runnableDebuggingMode="0">
      <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target_uid}"
        BuildableName="ChecklistBoteco.app" BlueprintName="ChecklistBoteco"
        ReferencedContainer="container:ChecklistBoteco.xcodeproj"/>
    </BuildableProductRunnable>
  </LaunchAction>
</Scheme>
"""
with open(os.path.join(scheme_dir, "ChecklistBoteco.xcscheme"), "w", encoding="utf-8") as f:
  f.write(scheme)

os.makedirs(PROJECT_DIR, exist_ok=True)
with open(os.path.join(PROJECT_DIR, "project.pbxproj"), "w", encoding="utf-8") as f:
  f.write(pbx)
print(f"Generated {PROJECT_DIR}/project.pbxproj (umbrella: {packages_url})")
print("Após commit em Packages/, atualize Package.resolved:")
print("  git -C Packages rev-parse HEAD")
print("  # cole a revision em project.xcworkspace/xcshareddata/swiftpm/Package.resolved")
print("  # ou: xcodebuild -resolvePackageDependencies (Xcode 14)")
