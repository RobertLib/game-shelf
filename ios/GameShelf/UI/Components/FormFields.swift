import SwiftUI

/// Inline validation message under a form field.
struct FieldError: View {
    let message: String?

    var body: some View {
        if let message {
            Label(message, systemImage: "exclamationmark.circle.fill")
                .font(.footnote)
                .foregroundStyle(.red)
                .accessibilityLabel("Error: \(message)")
        }
    }
}

/// Secure text field with a show/hide toggle.
struct PasswordField: View {
    let title: String
    @Binding var text: String
    var contentType: UITextContentType = .password
    var accessibilityIdentifier: String?

    @State private var isRevealed = false

    var body: some View {
        HStack {
            Group {
                if isRevealed {
                    TextField(title, text: $text)
                } else {
                    SecureField(title, text: $text)
                }
            }
            .textContentType(contentType)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .accessibilityIdentifier(accessibilityIdentifier ?? title)

            Button {
                isRevealed.toggle()
            } label: {
                Image(systemName: isRevealed ? "eye.slash" : "eye")
                    .foregroundStyle(Color.secondary)
                    .frame(minWidth: 28, minHeight: 28)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isRevealed ? "Hide password" : "Show password")
        }
    }
}

/// Labeled form text field: the label stays visible once a value is entered.
struct LabeledTextField: View {
    let title: String
    @Binding var text: String
    var prompt: String?

    var body: some View {
        LabeledContent(title) {
            TextField(title, text: $text, prompt: prompt.map { Text($0) })
                .multilineTextAlignment(.trailing)
        }
    }
}

/// Labeled text field offering matching values (e.g. from collection facets) while it is focused.
struct SuggestingTextField: View {
    let title: String
    @Binding var text: String
    let suggestions: [String]
    var prompt: String?
    var maxSuggestions = 8

    @FocusState private var isFocused: Bool

    private var matches: [String] {
        let needle = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let candidates = needle.isEmpty
            ? suggestions
            : suggestions.filter {
                $0.localizedStandardContains(needle) && $0.compare(needle, options: [.caseInsensitive, .diacriticInsensitive]) != .orderedSame
            }
        return Array(candidates.prefix(maxSuggestions))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            LabeledContent(title) {
                TextField(title, text: $text, prompt: prompt.map { Text($0) })
                    .multilineTextAlignment(.trailing)
                    .focused($isFocused)
                    .submitLabel(.done)
            }

            if isFocused, !matches.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: 6) {
                        ForEach(matches, id: \.self) { suggestion in
                            Button(suggestion) {
                                text = suggestion
                                isFocused = false
                            }
                            .font(.footnote)
                            .buttonStyle(.bordered)
                            .buttonBorderShape(.capsule)
                            .controlSize(.small)
                            .accessibilityHint("Fills in \(title.lowercased())")
                        }
                    }
                }
                .scrollIndicators(.hidden)
                .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.15), value: isFocused)
    }
}

/// Date that can be left empty: a toggle reveals the picker.
struct OptionalDatePicker: View {
    let title: String
    @Binding var date: Date?
    var range: ClosedRange<Date>?
    var defaultDate: Date = .now

    var body: some View {
        Toggle(title, isOn: Binding(
            get: { date != nil },
            set: { date = $0 ? (date ?? defaultDate) : nil }
        ))
        if let current = date {
            let binding = Binding<Date>(get: { current }, set: { date = $0 })
            Group {
                if let range {
                    DatePicker(title, selection: binding, in: range, displayedComponents: .date)
                } else {
                    DatePicker(title, selection: binding, displayedComponents: .date)
                }
            }
            .labelsHidden()
            .datePickerStyle(.compact)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }
}

#Preview {
    @Previewable @State var password = "secret-password"
    @Previewable @State var genre = "RP"
    @Previewable @State var date: Date? = .now
    Form {
        PasswordField(title: "Password", text: $password)
        FieldError(message: "Password must be 8–128 characters.")
        SuggestingTextField(title: "Genre", text: $genre, suggestions: ["RPG", "Action RPG", "Platformer"])
        OptionalDatePicker(title: "Purchase date", date: $date)
    }
}
