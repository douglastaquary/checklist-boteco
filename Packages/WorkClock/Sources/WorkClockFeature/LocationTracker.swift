import Foundation
import CoreLocation
import Combine

public final class LocationTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
  @Published public var location: CLLocation?
  @Published public var authorizationDenied = false
  @Published public var locationError: String?
  private let manager = CLLocationManager()

  public override init() {
    super.init()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyBest
  }

  public func start() {
    manager.requestWhenInUseAuthorization()
    manager.startUpdatingLocation()
  }

  public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard let latest = locations.last, latest.horizontalAccuracy >= 0 else { return }
    location = latest
    authorizationDenied = false
    locationError = nil
  }

  public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    locationError = error.localizedDescription
  }

  public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    switch manager.authorizationStatus {
    case .denied, .restricted:
      authorizationDenied = true
    case .authorizedAlways, .authorizedWhenInUse:
      authorizationDenied = false
      manager.startUpdatingLocation()
    default:
      break
    }
  }
}
