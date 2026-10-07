import SwiftUI

/// "Profile & settings"
struct ProfileView: View {
    let sync: SyncEngine

    @Environment(SessionStore.self) private var session
    @Environment(FacetsStore.self) private var facets
    @State private var isConfirmingSignOut = false
    @State private var syncError: String?

    private var status: SyncStatus { sync.status }

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

            Section("Collection") {
                LabeledContent("Games", value: Pluralization.games(facets.facets.totalItems))
                LabeledContent("Platforms", value: AppFormat.integer(facets.facets.platforms.count))
            }

            Section("Sync") {
                LabeledContent("Status", value: status.summary)
                LabeledContent("Last synced") {
                    TimelineView(.periodic(from: .now, by: 30)) { context in
                        Text(status.lastSyncedAt.map { AppFormat.relativeTime($0, now: context.date) } ?? "Never")
                    }
                }
                Button {
                    Task { await syncNow() }
                } label: {
                    Label("Sync now", systemImage: "arrow.triangle.2.circlepath")
                }
                .disabled(status.isSyncing)
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
            Button(status.pendingCount > 0 ? "Sign out anyway" : "Sign out", role: .destructive) {
                Task { await session.signOut() }
            }
        } message: {
            if status.pendingCount > 0 {
                Text(Pluralization.signOutWarning(unsyncedChanges: status.pendingCount))
            } else {
                Text("Your collection stays on the server. You can sign in again at any time.")
            }
        }
        .alert(
            "Couldn't sync",
            isPresented: Binding(get: { syncError != nil }, set: { if !$0 { syncError = nil } }),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(syncError ?? "") }
        )
    }

    private func syncNow() async {
        do {
            try await sync.syncNow()
        } catch {
            if !ErrorMessage.isCancellation(error) {
                syncError = ErrorMessage.message(for: error)
            }
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
    let sync = SyncEngine.preview()
    NavigationStack {
        ProfileView(sync: sync)
    }
    .environment(PreviewData.sessionStore(signedIn: true))
    .environment(FacetsStore(repository: sync.repository))
}
#endif
