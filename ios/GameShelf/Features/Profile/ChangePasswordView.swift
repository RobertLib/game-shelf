import SwiftUI

struct ChangePasswordView: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @State private var model = ChangePasswordViewModel()

    var body: some View {
        Form {
            Section {
                PasswordField(title: "Current password", text: $model.currentPassword, contentType: .password)
                FieldError(message: model.currentPasswordError)
            }
            Section {
                PasswordField(title: "New password", text: $model.newPassword, contentType: .newPassword)
                FieldError(message: model.newPasswordError)
                PasswordField(title: "Confirm new password", text: $model.confirmation, contentType: .newPassword)
                FieldError(message: model.confirmationError)
            } footer: {
                Text("Use 8–128 characters. Changing the password signs you out on all other devices.")
            }

            if let error = model.errorMessage {
                Section {
                    FormErrorBanner(message: error)
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }

            Section {
                Button {
                    Task { await model.submit(using: session) }
                } label: {
                    HStack {
                        Text("Change password")
                        if model.isSubmitting {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(model.isSubmitting)
            }
        }
        .navigationTitle("Change password")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Password changed", isPresented: $model.didSucceed) {
            Button("OK") { dismiss() }
        } message: {
            Text("You've been signed out on all other devices.")
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        ChangePasswordView()
    }
    .environment(PreviewData.sessionStore(signedIn: true))
}
#endif
