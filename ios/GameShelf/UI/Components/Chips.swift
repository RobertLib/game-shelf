import SwiftUI

/// Toggleable chip for multi-select filters.
struct SelectableChip: View {
    let title: String
    var count: Int?
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.caption.weight(.bold))
                }
                Text(title)
                if let count {
                    Text(count, format: .number)
                        .font(.caption.weight(.semibold))
                        .monospacedDigit()
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                        .background(isSelected ? AnyShapeStyle(.white) : AnyShapeStyle(.background), in: .capsule)
                }
            }
            .font(.subheadline)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .foregroundStyle(isSelected ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
            .background {
                Capsule()
                    .fill(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.fill.tertiary))
            }
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(count.map { "\(title), \(Pluralization.games($0))" } ?? title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Active filter shown under the search field; tapping removes it.
struct RemovableChip: View {
    let title: String
    let onRemove: () -> Void

    var body: some View {
        Button(action: onRemove) {
            HStack(spacing: 4) {
                Text(title)
                    .lineLimit(1)
                Image(systemName: "xmark")
                    .font(.caption2.weight(.bold))
            }
            .font(.footnote.weight(.medium))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .foregroundStyle(.tint)
            .background(Color.accentColor.opacity(0.14), in: .capsule)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityHint("Removes the filter")
    }
}

#Preview {
    VStack(spacing: 16) {
        HStack {
            SelectableChip(title: "PlayStation 2", count: 12, isSelected: true) {}
            SelectableChip(title: "Nintendo 64", count: 3, isSelected: false) {}
        }
        HStack {
            RemovableChip(title: "Genre: RPG") {}
            RemovableChip(title: "Favorites") {}
        }
    }
    .padding()
}
