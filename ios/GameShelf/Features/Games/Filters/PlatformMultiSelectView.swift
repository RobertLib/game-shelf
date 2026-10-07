import SwiftUI

/// All platforms grouped by manufacturer, multi-select, with counts from the collection.
struct PlatformMultiSelectView: View {
    @Binding var selection: Set<Platform>
    let facets: GameFacets

    @State private var searchText = ""

    var body: some View {
        List {
            ForEach(groups, id: \.group) { entry in
                Section(entry.group.label) {
                    ForEach(entry.platforms, id: \.self) { platform in
                        row(platform)
                    }
                }
            }
        }
        .overlay {
            if groups.isEmpty {
                ContentUnavailableView.search(text: searchText)
            }
        }
        .navigationTitle("Platforms")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search platforms")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Clear selection") { selection.removeAll() }
                    .disabled(selection.isEmpty)
            }
        }
    }

    private var groups: [(group: PlatformGroup, platforms: [Platform])] {
        Platform.grouped.compactMap { entry in
            let platforms = entry.platforms.filter { $0.matches(searchText) }
            return platforms.isEmpty ? nil : (entry.group, platforms)
        }
    }

    private func row(_ platform: Platform) -> some View {
        let isSelected = selection.contains(platform)
        let count = facets.count(of: platform)
        return Button {
            selection.toggleMembership(of: platform)
        } label: {
            HStack {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(Color(.tertiaryLabel)))
                    .imageScale(.large)
                Text(platform.label)
                    .foregroundStyle(Color.primary)
                    .fontWeight(count != nil ? .semibold : .regular)
                Spacer()
                if let count {
                    Text(Pluralization.games(count))
                        .font(.subheadline)
                        .foregroundStyle(Color.secondary)
                }
            }
            .contentShape(.rect)
        }
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

#if DEBUG
#Preview {
    @Previewable @State var selection: Set<Platform> = [.ps2]
    NavigationStack {
        PlatformMultiSelectView(selection: $selection, facets: PreviewData.facets)
    }
}
#endif
