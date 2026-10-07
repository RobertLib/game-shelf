import SwiftUI

/// "Sign in"
struct LoginView: View {
    @Environment(SessionStore.self) private var session
    @State private var model = LoginViewModel()
    @FocusState private var focusedField: Field?

    private enum Field: Hashable {
        case email, password
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                AuthHeader(title: "Game Shelf", subtitle: "Catalog your collection of computer and console games")

                if let notice = session.signOutNotice {
                    Label(notice, systemImage: "info.circle.fill")
                        .font(.subheadline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(.blue.opacity(0.1), in: .rect(cornerRadius: 12))
                }

                VStack(spacing: 12) {
                    AuthField(error: model.emailError) {
                        TextField("Email", text: $model.email)
                            .textContentType(.username)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($focusedField, equals: .email)
                            .submitLabel(.next)
                            .onSubmit { focusedField = .password }
                            .accessibilityIdentifier("login.email")
                    }
                    AuthField(error: model.passwordError) {
                        PasswordField(title: "Password", text: $model.password, contentType: .password, accessibilityIdentifier: "login.password")
                            .focused($focusedField, equals: .password)
                            .submitLabel(.go)
                            .onSubmit(submit)
                    }
                }

                FormErrorBanner(message: model.errorMessage)

                Button(action: submit) {
                    ZStack {
                        Text("Sign in")
                            .opacity(model.isSubmitting ? 0 : 1)
                        if model.isSubmitting {
                            ProgressView()
                                .tint(.white)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(model.isSubmitting)

                NavigationLink(value: AuthRoute.register) {
                    Text("Don't have an account? \(Text("Create one").bold())")
                }
                .font(.subheadline)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color(.systemGroupedBackground))
        .animation(.default, value: model.errorMessage)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func submit() {
        focusedField = nil
        Task { await model.submit(using: session) }
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        LoginView()
    }
    .environment(PreviewData.sessionStore(signedIn: false))
}
#endif
