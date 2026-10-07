import SwiftUI

/// Small tinted capsule label, e.g. collection status or completeness.
struct BadgeView: View {
    let text: String
    var tint: Color = .secondary

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .foregroundStyle(tint)
            .background(tint.opacity(0.14), in: .capsule)
    }
}

extension CollectionStatus {
    var tint: Color {
        switch self {
        case .owned: .green
        case .wishlist: .blue
        case .preordered: .purple
        case .lent: .orange
        case .forSale: .teal
        case .sold, .unknown: .gray
        }
    }
}

extension Completeness {
    var tint: Color {
        switch self {
        case .sealed, .cib: .green
        case .gameAndBox, .gameAndManual: .mint
        case .loose, .boxOnly, .unknown: .gray
        }
    }
}

extension Condition {
    var tint: Color {
        switch self {
        case .mint, .nearMint: .green
        case .veryGood, .good: .blue
        case .fair: .orange
        case .poor: .red
        case .unknown: .gray
        }
    }
}

#Preview {
    VStack(alignment: .leading) {
        BadgeView(text: CollectionStatus.wishlist.label, tint: CollectionStatus.wishlist.tint)
        BadgeView(text: Completeness.cib.label, tint: Completeness.cib.tint)
        BadgeView(text: Condition.fair.label, tint: Condition.fair.tint)
    }
    .padding()
}
