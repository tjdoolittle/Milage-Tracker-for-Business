import StoreKit
import SwiftUI

/// App root: applies the theme choice, shows the first-launch walkthrough, and asks for a rating once.
struct RootView: View {
    @EnvironmentObject private var store: Store
    @Environment(\.requestReview) private var requestReview

    private var scheme: ColorScheme? {
        switch store.data.settings.theme {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    private var loggedCount: Int { store.data.trips.filter { $0.status == TripStatus.logged }.count }

    var body: some View {
        HomeView()
            .preferredColorScheme(scheme)
            .fullScreenCover(isPresented: Binding(
                get: { !store.data.settings.introDismissed },
                set: { shown in if !shown { finishWalkthrough() } })) {
                WalkthroughView { finishWalkthrough() }
                    .preferredColorScheme(scheme)
            }
            .onChange(of: loggedCount >= 10, initial: true) { _, enough in
                guard enough, !store.data.settings.reviewAsked, !store.demo else { return }
                var s = store.data.settings
                s.reviewAsked = true
                store.setSettings(s)
                // Let the home screen settle first; iOS decides whether the prompt actually shows.
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { requestReview() }
            }
    }

    private func finishWalkthrough() {
        guard !store.data.settings.introDismissed else { return }
        var s = store.data.settings
        s.introDismissed = true
        s.seenNews = Store.whatsNewId
        store.setSettings(s)
    }
}

/// First-launch walkthrough. Settings can show it again.
struct WalkthroughView: View {
    var onDone: () -> Void
    @State private var page = 0
    private let pages = Store.walkthrough

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("TAX MILEAGE TRACKER").font(Fonts.sign(13)).tracking(0.8).foregroundStyle(Palette.signText)
                Spacer()
                if page < pages.count - 1 {
                    Button("Skip", action: onDone).font(Fonts.bodyBold(16)).foregroundStyle(Palette.muted)
                }
            }
            .padding(.horizontal, 24).padding(.top, 12).frame(minHeight: 44)

            TabView(selection: $page) {
                ForEach(pages.indices, id: \.self) { i in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            Text("STEP \(i + 1) OF \(pages.count)")
                                .font(Fonts.sign(14)).tracking(0.8).foregroundStyle(Palette.signInk)
                                .padding(.horizontal, 16).padding(.vertical, 10)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Palette.sign))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.signInk.opacity(0.6), lineWidth: 2))
                            Text(pages[i].0).font(Fonts.sign(30)).foregroundStyle(Palette.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(pages[i].1).font(Fonts.body(18)).lineSpacing(6).foregroundStyle(Palette.ink)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(maxWidth: 560, alignment: .leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24).padding(.top, 40)
                    }
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            HStack(spacing: 8) {
                ForEach(pages.indices, id: \.self) { i in
                    Capsule().fill(i == page ? Palette.sign : Palette.line)
                        .frame(width: i == page ? 22 : 8, height: 8)
                        .onTapGesture { withAnimation { page = i } }
                }
            }
            .padding(.vertical, 16)
            .animation(.easeInOut(duration: 0.2), value: page)

            HStack(spacing: 10) {
                if page > 0 {
                    Button("Back") { withAnimation { page -= 1 } }
                        .buttonStyle(BigButtonStyle(kind: .outline)).frame(width: 120)
                }
                Button(page == pages.count - 1 ? "Get started" : "Next") {
                    if page == pages.count - 1 { onDone() } else { withAnimation { page += 1 } }
                }
                .buttonStyle(BigButtonStyle())
            }
            .padding(.horizontal, 24).padding(.bottom, 12)
        }
        .background(Palette.bg.ignoresSafeArea())
    }
}
