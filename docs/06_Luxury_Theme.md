# ✨ Luxury Theme

> Version: **1.0**
>
> Status: **Approved** (2026-10-27)

---

← **[Back to README](../README.md)**

---

## Purpose

The one look of **AMBiFy**: graphite surfaces and metallic gold, the same on every phone. Every new screen follows it; a change to it is approved by the owner first (mock-ups on the design canvas, then the test app).

The code lives in `app/.../ui/theme/`: colors in `Color.kt` (`Luxury`), the card in `LuxuryCard.kt`, the Material color scheme in `Theme.kt`.

---

## Rules

- **Dark only.** No light variant and no colors taken from the phone's wallpaper (Android dynamic color is off).
- **Gold, not copper.** Pure metallic gold without brown or red; brown shades (e.g. `#C9A85B`, `#8A6A2B`) were rejected.
- **Cards** (`LuxuryCard`): graphite vertical gradient, 1 dp gold gradient border, 20 dp corners and a shadow under them, so they stand out of the background.
- **Card titles** (`LuxurySectionTitle`): small gold capitals with wide letter spacing, e.g. "BASIC · W CENIE".
- **Rows** in a card are separated by a thin gold line (`LuxuryDivider`).
- **Popups** look like cards with a brighter border (`PopupBorderBrush`) and a stronger shadow.
- **Switches**: gold track with a dark thumb when on.
- **Secondary buttons** ("Kup"): gold outline, faint gold fill, gold text.

---

## Colors

| Name | Value | Use |
|------|-------|-----|
| Background | `#121214` | Screen background |
| CardTop → CardBottom | `#232327` → `#19191C` | Card gradient |
| PopupTop | `#2A2A2F` | Popup and dialog surface |
| Text | `#F1ECE0` | Main text |
| TextMuted | `#A9A9B1` | Secondary text |
| Disabled | `#85858D` | Greyed-out items (e.g. an extra of a switched-off module) |
| GoldBase | `#D4AF37` | Classic metallic gold |
| GoldLight | `#F7E08A` | Highlight: start of borders, top of gold fills |
| GoldHighlight | `#FFF1B8` | Brightest highlight (popup border) |
| GoldDeep / GoldShadow | `#8C7322` / `#A88A2A` | Dark side of the gold gradients |
| GoldButton | `#C9A227` | Bottom of gold fills, switch track |
| GoldText | `#E6C65C` | Gold text and marks on graphite |
| GoldLink | `#D9B840` | Small links, button outlines |

Borders: linear gradient GoldLight → GoldDeep → GoldBase → GoldShadow.

---

## Related Documents

| Document | Description |
|----------|-------------|
| [README](../README.md) | Project overview |
| [Naming Convention](03_Naming_Convention.md) | Naming standards, module bar labels |
| [Modules](05_Modules.md) | Planned modules |
