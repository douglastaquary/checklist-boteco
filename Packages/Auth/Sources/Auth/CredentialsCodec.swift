import Models
enum CredentialsCodec {
  static func pack(username: String, password: String) -> String {
    "\(username.trimmingCharacters(in: .whitespacesAndNewlines))\u{0000}\(password)"
  }

  static func unpack(_ payload: String) -> UnlockedLoginCredentials {
    guard let index = payload.firstIndex(of: "\u{0000}"), index > payload.startIndex else {
      return UnlockedLoginCredentials(username: payload, password: "")
    }
    let username = String(payload[..<index])
    let password = String(payload[payload.index(after: index)...])
    return UnlockedLoginCredentials(username: username, password: password)
  }
}
