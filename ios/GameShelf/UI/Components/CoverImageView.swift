import SwiftUI

/// Game cover loaded from `coverImageUrl`, with a placeholder showing the platform's short name.
struct CoverImageView: View {
    let url: URL?
    let platform: Platform
    var cornerRadius: CGFloat = 8

    var body: some View {
        ZStack {
            placeholder
            if let url {
                AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFill()
                            .transition(.opacity)
                    }
                }
            }
        }
        .clipShape(.rect(cornerRadius: cornerRadius))
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius)
                .strokeBorder(.separator, lineWidth: 0.5)
        }
        .accessibilityHidden(true)
    }

    private var placeholder: some View {
        Rectangle()
            .fill(platform.group.tint.gradient.opacity(0.25))
            .overlay {
                Text(platform.shortName)
                    .font(.system(.caption, design: .rounded, weight: .bold))
                    .foregroundStyle(platform.group.tint)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .padding(4)
            }
    }
}

extension PlatformGroup {
    var tint: Color {
        switch self {
        case .pcAndMac: .gray
        case .sony: .blue
        case .microsoft: .green
        case .nintendo: .red
        case .sega: .indigo
        case .atari: .orange
        case .otherConsoles: .teal
        case .homeComputers: .brown
        case .other: .secondary
        }
    }
}

#Preview {
    HStack(spacing: 16) {
        CoverImageView(url: nil, platform: .ps2)
            .frame(width: 60, height: 84)
        CoverImageView(url: nil, platform: .megaDrive)
            .frame(width: 60, height: 84)
        CoverImageView(url: URL(string: "https://picsum.photos/seed/zelda/300/420"), platform: .n64)
            .frame(width: 60, height: 84)
    }
    .padding()
}
