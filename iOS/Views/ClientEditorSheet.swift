import SwiftUI

struct ClientEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @FocusState private var isNameFocused: Bool
    @State private var name: String
    @State private var selectedDetent: PresentationDetent = .medium

    let title: String
    let confirmTitle: String
    let onSave: (String) -> Void

    init(
        title: String,
        initialName: String,
        confirmTitle: String = "save".localized,
        onSave: @escaping (String) -> Void
    ) {
        self.title = title
        self.confirmTitle = confirmTitle
        self.onSave = onSave
        _name = State(initialValue: initialName)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.clear.oakBackground()
                editorContent
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { cancelToolbarItem }
            .safeAreaInset(edge: .bottom) { saveBar }
        }
        .presentationDetents([.medium, .large], selection: $selectedDetent)
        .presentationDragIndicator(.visible)
        .task {
            selectedDetent = .large
            await Task.yield()
            isNameFocused = true
        }
    }

    private var editorContent: some View {
        ScrollView {
            VStack(spacing: 18) {
                profilePreview
                VStack(alignment: .leading, spacing: 10) {
                    Text("client_editor_body".localized)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    nameField
                }
                .padding(18)
                .oakCardStyle(.glass, cornerRadius: 22)
            }
            .padding(20)
            .frame(maxHeight: .infinity, alignment: .top)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private var nameField: some View {
        TextField("client_name_hint".localized, text: $name)
            .focused($isNameFocused)
            .textInputAutocapitalization(.words)
            .submitLabel(.done)
            .onSubmit(save)
            .onChange(of: name) { _, value in limitName(value) }
            .padding(14)
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private var profilePreview: some View {
        VStack(spacing: 10) {
            Text(profileInitial)
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .frame(width: 76, height: 76)
                .background(profileGradient, in: Circle())
                .shadow(color: OAKPalette.accent.opacity(0.24), radius: 18, y: 8)
            Text(displayName)
                .font(.title3.weight(.semibold))
                .lineLimit(1)
        }
        .accessibilityElement(children: .combine)
    }

    private var saveBar: some View {
        Button(action: save) {
            Text(confirmTitle)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .disabled(trimmedName.isEmpty)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial)
    }

    @ToolbarContentBuilder
    private var cancelToolbarItem: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("cancel".localized) { dismiss() }
        }
    }

    private var profileGradient: LinearGradient {
        LinearGradient(
            colors: [OAKPalette.heroStart, OAKPalette.heroEnd],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var displayName: String {
        trimmedName.isEmpty ? "client_name_label".localized : trimmedName
    }

    private var profileInitial: String {
        guard let first = trimmedName.first else { return "+" }
        return String(first).uppercased()
    }

    private func limitName(_ value: String) {
        guard value.count > 60 else { return }
        name = String(value.prefix(60))
    }

    private func save() {
        guard !trimmedName.isEmpty else { return }
        onSave(trimmedName)
        dismiss()
    }
}
