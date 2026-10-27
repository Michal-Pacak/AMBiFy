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
- **Floating cards** (a rigid raft on water): a card is not a button. Touched anywhere outside its controls, it tips up to 5° towards the finger (the far side rises) and sinks slightly; the edge on that side touches the water and two gold rings, 0.9 s apart, spread lazily from that edge **on the water under the cards** (`ui/theme/Water.kt`, drawn behind all content — never on the card): about 27 dp/s to 120 dp, thinning and fading over 4.5 s. Let go, the raft rocks to and fro with a shrinking swing (~1.1 s per swing) before it settles: each time an edge dips it sends new, fainter rings, and each time it passes level the two edges across the swing send one much weaker ring each (a fifth of the last dip). The splash points are on the real (tilted, rounded) outline. A touch on a control inside (button, switch, link, clickable row) does only what the control does — no rocking, no rings; a card that opens something has a round gold `LuxuryOpenButton`. Screens are transparent so the water shows between cards. Scrolling cancels the effect. Off in Ustawienia → Aplikacja → "Animacje kart" (per phone) and when Android's animations are off.
- **Avatars** (`ui/components/PersonAvatar.kt`, the only place that draws a person): initials of the first and last word of the name on the person's color, thin gold rim. Same initials in one community → a different shape, in the order circle, square, triangle, star, hexagon, diamond. Shape and color are handed out once per community by the server and never change when others join.

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
