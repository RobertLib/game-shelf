import SwiftUI

/// "Create account"
struct RegisterView: View {
    @Environment(SessionStore.self) private var session
    @State private var model = RegisterViewModel()
    @FocusState private var focusedField: Field?

    private enum Field: Hashable {
        case email, displayName, password, confirmation
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Text("Create an account to keep your collection at hand.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                VStack(spacing: 12) {
                    AuthField(error: model.emailError) {
                        TextField("Email", text: $model.email)
                            .textContentType(.username)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($focusedField, equals: .email)
                            .submitLabel(.next)
                            .onSubmit { focusedField = .displayName }
                            .accessibilityIdentifier("register.email")
                    }
                    AuthField(error: model.displayNameError) {
                        TextField("Display name (optional)", text: $model.displayName)
                            .textContentType(.nickname)
                            .focused($focusedField, equals: .displayName)
                            .accessibilityIdentifier("register.displayName")
                            .submitLabel(.next)
                            .onSubmit { focusedField = .password }
                    }
                    AuthField(error: model.passwordError) {
                        PasswordField(title: "Password (8–128 characters)", text: $model.password, contentType: .newPassword, accessibilityIdentifier: "register.password")
                            .focused($focusedField, equals: .password)
                            .submitLabel(.next)
                            .onSubmit { focusedField = .confirmation }
                    }
                    AuthField(error: model.confirmationError) {
                        PasswordField(title: "Confirm password", text: $model.passwordConfirmation, contentType: .newPassword, accessibilityIdentifier: "register.confirmation")
                            .focused($focusedField, equals: .confirmation)
                            .submitLabel(.go)
                            .onSubmit(submit)
                    }
                }

                FormErrorBanner(message: model.errorMessage)

                Button(action: submit) {
                    ZStack {
                        Text("Create account")
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
            }
            .padding(20)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color(.systemGroupedBackground))
        .animation(.default, value: model.errorMessage)
        .navigationTitle("Create account")
    }

    private func submit() {
        focusedField = nil
        Task { await model.submit(using: session) }
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        RegisterView()
    }
    .environment(PreviewData.sessionStore(signedIn: false))
}
#endif
