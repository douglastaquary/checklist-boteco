import Foundation
import Models
public enum APIError: Error, LocalizedError, Sendable {
  case invalidURL
  case invalidResponse
  case http(status: Int, message: String)
  case decoding(Error)
  case transport(Error)

  public var errorDescription: String? {
    switch self {
    case .invalidURL: return "URL da API inválida."
    case .invalidResponse: return "Resposta inválida do servidor."
    case let .http(_, message): return message
    case .decoding: return "Não foi possível interpretar a resposta do servidor."
    case let .transport(error): return AppErrorMapper.toUserMessage(error)
    }
  }
}

public enum AppErrorMapper {
  public static func toUserMessage(_ error: Error) -> String {
    if let api = error as? APIError, let description = api.errorDescription {
      return description
    }
    if let urlError = error as? URLError {
      switch urlError.code {
      case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .cannotConnectToHost:
        return "Sem conexão com a internet ou servidor indisponível. Tente novamente em instantes."
      case .timedOut:
        return "A conexão demorou demais. Verifique a internet e tente novamente."
      default:
        break
      }
    }
    let message = error.localizedDescription
    if containsCaseInsensitive(message, "Backend não configurado") {
      return "Este aparelho não está configurado para acessar o servidor. Verifique a URL da API."
    }
    if containsCaseInsensitive(message, "login novamente") {
      return "Sua sessão expirou. Saia e entre novamente."
    }
    if containsCaseInsensitive(message, "Email ou senha") {
      return "Usuário ou senha inválidos."
    }
    if !message.isEmpty { return message }
    return "Não foi possível concluir a operação. Tente novamente."
  }

  public static func fromHTTP(status: Int, body: String) -> String {
    if let parsed = parseMessage(body) { return parsed }
    switch status {
    case 400: return "Os dados enviados são inválidos. Revise as informações e tente novamente."
    case 401: return "Sessão expirada ou credenciais inválidas. Faça login novamente."
    case 403: return "Você não tem permissão para esta ação."
    case 404: return "Recurso não encontrado no servidor."
    case 408, 504: return "O servidor demorou para responder. Tente novamente."
    case 503:
      return "Chat de IA indisponível. Configure OPENAI_API_KEY no backend e reinicie o servidor."
    case 500...599: return "O servidor encontrou um problema. Tente novamente em instantes."
    default: return "Não foi possível concluir a operação (erro \(status))."
    }
  }

  private static func containsCaseInsensitive(_ haystack: String, _ needle: String) -> Bool {
    haystack.range(of: needle, options: .caseInsensitive) != nil
  }

  private static func parseMessage(_ body: String) -> String? {
    guard let data = body.data(using: .utf8),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let message = json["message"] as? String,
          !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    else { return nil }
    return message
  }
}
