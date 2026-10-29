import SwiftUI
import UIKit

/// Highway guide-sign palette: sign green, reflective amber, concrete neutrals. Each color has a dark-mode twin.
enum Palette {
    static let bg = Color(light: 0xECEFEB, dark: 0x0E1311)
    static let surface = Color(light: 0xFFFFFF, dark: 0x172019)
    static let ink = Color(light: 0x18211D, dark: 0xE5ECE8)
    static let muted = Color(light: 0x5B6862, dark: 0x94A39B)
    static let line = Color(light: 0xD3D9D4, dark: 0x29342F)
    static let sign = Color(light: 0x0A6A4A, dark: 0x0D7653)
    static let signInk = Color.white
    static let signSoft = Color(light: 0xCFE7DC, dark: 0xB9DCCC)
    static let signText = Color(light: 0x0A6A4A, dark: 0x5FC59A)
    static let amber = Color(light: 0xC98A07, dark: 0xF0B43C)
    static let amberBg = Color(light: 0xFFF3D6, dark: 0x2E2611)
    static let danger = Color(light: 0xB3261E, dark: 0xF08A82)
    static let odoBg = Color(light: 0x111513, dark: 0x050706)
    static let odoFg = Color(hex: 0xF3F3EE)
}

/// Overpass is modeled on Highway Gothic, the lettering on US road signs.
enum Fonts {
    static func sign(_ size: CGFloat) -> Font { .custom("OverpassRoman-ExtraBold", size: size) }
    static func signBold(_ size: CGFloat) -> Font { .custom("OverpassRoman-Bold", size: size) }
    static func mono(_ size: CGFloat) -> Font { .custom("OverpassMono-SemiBold", size: size) }
    static func body(_ size: CGFloat = 16) -> Font { .custom("AtkinsonHyperlegible-Regular", size: size, relativeTo: .body) }
    static func bodyBold(_ size: CGFloat = 16) -> Font { .custom("AtkinsonHyperlegible-Bold", size: size, relativeTo: .body) }
    static func label(_ size: CGFloat = 13) -> Font { sign(size) }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

/// Uppercase sign lettering with a little tracking.
struct SignLabel: View {
    var text: String
    var size: CGFloat = 13
    var color: Color = Palette.muted
    var body: some View {
        Text(text.uppercased()).font(Fonts.label(size)).tracking(size * 0.1).foregroundStyle(color)
    }
}

enum Fmt {
    static let usd: NumberFormatter = {
        let f = NumberFormatter(); f.numberStyle = .currency; f.locale = Locale(identifier: "en_US"); return f
    }()
    static func money(_ v: Double) -> String { usd.string(from: NSNumber(value: v)) ?? String(format: "$%.2f", v) }
    static func mi(_ v: Double) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.minimumFractionDigits = 1; f.maximumFractionDigits = 1
        f.locale = Locale(identifier: "en_US")
        return f.string(from: NSNumber(value: v)) ?? String(format: "%.1f", v)
    }
    /// 0.76 -> "76", 0.725 -> "72.5"
    static func cents(_ rate: Double) -> String {
        let c = (rate * 1000).rounded() / 10
        return c.truncatingRemainder(dividingBy: 1) == 0 ? String(format: "%.0f", c) : String(format: "%.1f", c)
    }
    static let months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
    static let mon = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

    /// "2026-07-01" -> "Jul 1, 2026"
    static func longDate(_ iso: String) -> String {
        let d = Store.date(iso)
        let c = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: d)
        return "\(mon[(c.month ?? 1) - 1]) \(c.day ?? 1), \(c.year ?? 0)"
    }

    static func dayBefore(_ iso: String) -> String {
        let d = Calendar(identifier: .gregorian).date(byAdding: .day, value: -1, to: Store.date(iso)) ?? Store.date(iso)
        return Store.dayFormatter.string(from: d)
    }
}

struct BigButtonStyle: ButtonStyle {
    enum Kind { case primary, outline, amber, danger }
    var kind: Kind = .primary

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Fonts.sign(16)).tracking(0.6).textCase(.uppercase)
            .lineLimit(1).minimumScaleFactor(0.8)
            .frame(maxWidth: .infinity, minHeight: 52)
            .padding(.horizontal, 12)
            .foregroundStyle(fg)
            .background(RoundedRectangle(cornerRadius: 12).fill(bg))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(border, lineWidth: kind == .outline || kind == .danger ? 2 : 0))
            .opacity(configuration.isPressed ? 0.75 : 1)
            .contentShape(RoundedRectangle(cornerRadius: 12))
    }

    private var fg: Color {
        switch kind {
        case .primary: return Palette.signInk
        case .outline: return Palette.ink
        case .amber: return Color(hex: 0x1A1405)
        case .danger: return Palette.danger
        }
    }
    private var bg: Color {
        switch kind {
        case .primary: return Palette.sign
        case .amber: return Palette.amber
        case .outline, .danger: return .clear
        }
    }
    private var border: Color { kind == .danger ? Palette.danger : Palette.ink }
}
