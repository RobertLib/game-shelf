import SwiftUI

/// Account deletion (App Store guideline 5.1.1(v)).
struct DeleteAccountView: View {
    @Environment(SessionStore.self) private var session
    @State private var model = DeleteAccountViewModel()

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Label("This can't be undone", systemImage: "exclamationmark.triangle.fill")
                        .font(.headline)
                        .foregroundStyle(.red)
                    Text("Deleting your account permanently removes your profile and your whole game collection, including all notes, prices and ratings. The data can't be recovered.")
                        .font(.subheadline)
                }
                .padding(.vertical, 4)
            }

            Section {
                PasswordField(title: "Password", text: $model.password, contentType: .password)
                FieldError(message: model.passwordError)
            } header: {
                Text("Confirm with your password")
            }

            if let error = model.errorMessage {
                Section {
                    FormErrorBanner(message: error)
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }

            Section {
                Button(role: .destructive, action: model.requestDeletion) {
                    HStack {
                        Text("Delete account and collection")
                        if model.isDeleting {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(model.isDeleting)
            }
        }
        .navigationTitle("Delete account")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Delete account?", isPresented: $model.isConfirming, titleVisibility: .visible) {
            Button("Delete account", role: .destructive) {
                Task { await model.delete(using: session) }
            }
        } message: {
            Text("Your account and your whole collection will be permanently deleted.")
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeleteAccountView()
    }
    .environment(PreviewData.sessionStore(signedIn: true))
}
#endif
