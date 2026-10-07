import SwiftUI

struct GameRowView: View {
    let game: Game

    @ScaledMetric(relativeTo: .headline) private var coverWidth: CGFloat = 52

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            CoverImageView(url: game.coverURL, platform: game.platform, cornerRadius: 6)
                .frame(width: coverWidth, height: coverWidth * 1.4)

            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(game.title)
                        .font(.headline)
                        .lineLimit(2)
                    Spacer(minLength: 0)
                    if game.favorite {
                        Image(systemName: "star.fill")
                            .font(.subheadline)
                            .foregroundStyle(.yellow)
                            .accessibilityLabel("Favorite")
                    }
                }

                Text(secondaryLine)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)

                if hasBadges {
                    FlowLayout(spacing: 4, lineSpacing: 4) {
                        if game.status != .owned {
                            BadgeView(text: game.status.label, tint: game.status.tint)
                        }
                        if let completeness = game.completeness {
                            BadgeView(text: completeness.label, tint: completeness.tint)
                        }
                        if let condition = game.condition {
                            BadgeView(text: condition.label, tint: condition.tint)
                        }
                    }
                    .padding(.top, 2)
                }

                if let value = game.estimatedValue {
                    Text("Value \(AppFormat.currency(value, code: game.currency))")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private var secondaryLine: String {
        [game.platform.label, game.region?.shortLabel, game.releaseYear.map(String.init)]
            .compactMap(\.self)
            .joined(separator: " · ")
    }

    private var hasBadges: Bool {
        game.status != .owned || game.completeness != nil || game.condition != nil
    }
}

#if DEBUG
#Preview {
    List(PreviewData.games) { game in
        GameRowView(game: game)
    }
    .listStyle(.plain)
}
#endif
