import SwiftUI

/// Single-choice platform list grouped by manufacturer, searchable, with the
/// collection's platforms offered first.
struct PlatformPickerView: View {
    @Binding var selection: Platform?
    var collectionPlatforms: [Platform] = []

    @State private var searchText = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            if searchText.isEmpty, !collectionPlatforms.isEmpty {
                Section("In your collection") {
                    ForEach(collectionPlatforms, id: \.self, content: row)
                }
            }
            ForEach(filteredGroups, id: \.group) { entry in
                Section(entry.group.label) {
                    ForEach(entry.platforms, id: \.self, content: row)
                }
            }
        }
        .overlay {
            if filteredGroups.isEmpty {
                ContentUnavailableView.search(text: searchText)
            }
        }
        .navigationTitle("Platform")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search platforms")
    }

    private var filteredGroups: [(group: PlatformGroup, platforms: [Platform])] {
        Platform.grouped.compactMap { entry in
            let platforms = entry.platforms.filter { $0.matches(searchText) }
            return platforms.isEmpty ? nil : (entry.group, platforms)
        }
    }

    private func row(_ platform: Platform) -> some View {
        Button {
            selection = platform
            dismiss()
        } label: {
            HStack {
                Text(platform.label)
                    .foregroundStyle(Color.primary)
                Spacer()
                if platform == selection {
                    Image(systemName: "checkmark")
                        .foregroundStyle(.tint)
                        .fontWeight(.semibold)
                }
            }
            .contentShape(.rect)
        }
        .accessibilityAddTraits(platform == selection ? .isSelected : [])
    }
}

extension Platform {
    func matches(_ query: String) -> Bool {
        let query = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return true }
        return label.localizedStandardContains(query)
            || shortName.localizedStandardContains(query)
            || group.label.localizedStandardContains(query)
    }
}

#Preview {
    @Previewable @State var selection: Platform? = .ps2
    NavigationStack {
        PlatformPickerView(selection: $selection, collectionPlatforms: [.ps2, .n64, .snes])
    }
}
