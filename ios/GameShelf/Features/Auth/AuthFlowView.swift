import SwiftUI

enum AuthRoute: Hashable {
    case register
}

/// Signed-out flow: login with a pushed registration screen.
struct AuthFlowView: View {
    @State private var path: [AuthRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            LoginView()
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case .register: RegisterView()
                    }
                }
        }
        #if DEBUG
        .task {
            if DebugLaunchOptions.current.consume(.register) {
                path = [.register]
            }
        }
        #endif
    }
}

/// Branded header shared by the auth screens.
struct AuthHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "books.vertical.fill")
                .font(.system(size: 40, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 84, height: 84)
                .background(
                    LinearGradient(
                        colors: [Color(red: 0.42, green: 0.36, blue: 0.95), Color(red: 0.18, green: 0.14, blue: 0.5)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    in: .rect(cornerRadius: 20)
                )
                .accessibilityHidden(true)
            Text(title)
                .font(.largeTitle.bold())
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 24)
    }
}

/// Rounded input container for the auth screens.
struct AuthField<Content: View>: View {
    let error: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            content
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(error == nil ? Color.clear : Color.red, lineWidth: 1)
                }
            FieldError(message: error)
                .padding(.horizontal, 4)
        }
    }
}

/// Error banner for failed submissions.
struct FormErrorBanner: View {
    let message: String?

    var body: some View {
        if let message {
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .font(.subheadline)
                .foregroundStyle(.red)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(.red.opacity(0.1), in: .rect(cornerRadius: 12))
                .transition(.opacity)
        }
    }
}
