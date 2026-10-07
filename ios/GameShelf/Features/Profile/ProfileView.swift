import SwiftUI

/// "Profile & settings"
struct ProfileView: View {
    @Environment(SessionStore.self) private var session
    @Environment(FacetsStore.self) private var facets
    @State private var isConfirmingSignOut = false

    var body: some View {
        List {
            if let user = session.user {
                Section {
                    ProfileHeader(user: user)
                }
                Section("Account") {
                    LabeledContent("Email", value: user.email)
                    LabeledContent("Display name", value: user.displayName?.nilIfBlank ?? "Not set")
                    LabeledContent("Member since", value: user.createdAt.formatted(date: .long, time: .omitted))
                }
            }

            if let total = facets.facets?.totalItems {
                Section("Collection") {
                    LabeledContent("Games", value: Pluralization.games(total))
                    LabeledContent("Platforms", value: AppFormat.integer(facets.facets?.platforms.count ?? 0))
                }
            }

            Section("Security") {
                NavigationLink(value: MainRoute.changePassword) {
                    Label("Change password", systemImage: "key")
                }
                Button {
                    isConfirmingSignOut = true
                } label: {
                    Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }

            Section {
                NavigationLink(value: MainRoute.deleteAccount) {
                    Label("Delete account", systemImage: "person.crop.circle.badge.xmark")
                        .foregroundStyle(.red)
                }
            } footer: {
                Text("Deletes your account and your whole collection. This can't be undone.")
            }

            Section {
            } footer: {
                Text("Game Shelf \(AppConfiguration.appVersion)")
                    .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("Profile")
        .task {
            await session.refreshProfile()
        }
        .confirmationDialog("Sign out?", isPresented: $isConfirmingSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) {
                Task { await session.signOut() }
            }
        } message: {
            Text("Your collection stays on the server. You can sign in again at any time.")
        }
    }
}

private struct ProfileHeader: View {
    let user: User

    var body: some View {
        HStack(spacing: 16) {
            Text(initials)
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 60, height: 60)
                .background(Color.accentColor.gradient, in: .circle)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(user.displayName?.nilIfBlank ?? "Collector")
                    .font(.headline)
                Text(user.email)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private var initials: String {
        let source = user.displayName?.nilIfBlank ?? user.email
        let words = source.split(whereSeparator: { $0 == " " || $0 == "@" || $0 == "." }).prefix(2)
        return words.compactMap(\.first).map { String($0).uppercased() }.joined()
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        ProfileView()
    }
    .environment(PreviewData.sessionStore(signedIn: true))
    .environment(FacetsStore(service: PreviewGameService(), facets: PreviewData.facets))
}
#endif
