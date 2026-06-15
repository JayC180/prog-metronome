Prog Metronome supports custom color themes via JSON files (`.json`). Import a theme in Settings -> Appearance -> Import Theme.

## File format

```json
{
  "name": "My Theme",

  "bg0":    "#0A0A0A",
  "bg1":    "#111111",
  "bg2":    "#161616",
  "bg3":    "#1A1A1A",

  "border0": "#181818",
  "border1": "#222222",

  "textPrimary":   "#E0E0E0",
  "textSecondary": "#888888",
  "textMuted":     "#444444",
  "textDim":       "#2A2A2A",

  "accent":       "#4DAA7A",
  "accentBright": "#7EDE9A",

  "caution": "#B0A030",
  "danger":  "#DE7A7A",

  "muteColor": "#E0843A",
  "soloColor": "#4DA6D4",

  "beatActiveBg":       "#1A2822",
  "beatActiveBorder":   "#2A4A3A",
  "beatSelectedBg":     "#1A2535",
  "beatSelectedBorder": "#4A7ABE",
  "beatSelectedText":   "#8AB4EE",

  "bracketText": "#3D6A3D",
  "repeatText":  "#5A5AAA",
  "setBpmText":  "#7A7A20",

  "trackActiveBg": "#0F1A0F",
  "deleteText":    "#333333",
  "thumbColor":    "#CCCCCC",

  "defaultPanelAlpha": 1.0
}
```

Unknown keys are ignored. Missing keys fall back to the Obsidian (default theme) values.

---

## Color fields

Colors are hex strings: `#RRGGBB` or `#AARRGGBB`. `#` is optional.

### Surfaces

| Key | Where it appears |
|-----|-----------------|
| `bg0` | Numpad background, recessed items (brackets, repeats, rest beats) |
| `bg1` | Track row backgrounds |
| `bg2` | Dialog and overlay backgrounds |
| `bg3` | Button/chip backgrounds |

### Borders

| Key | Where it appears |
|-----|-----------------|
| `border0` | Hairline dividers, very subtle separators |
| `border1` | Standard UI borders on buttons, cards, dialogs |

### Text

| Key | Where it appears |
|-----|-----------------|
| `textPrimary` | Dialog titles, BPM display, main content |
| `textSecondary` | Subtitles, labels, sound names |
| `textMuted` | Button labels, secondary hints |
| `textDim` | Watermark text, disabled state hints |

### Accent

| Key | Where it appears |
|-----|-----------------|
| `accent` | Active track name, selected beat text, volume slider fill, toggle on-state, focused input border, BPM pill |
| `accentBright` | Currently-playing beat border and text, play-button triangle |

Derived colors:
- **accentBg** = `accent` at 12% alpha - tinted button/chip backgrounds
- **accentBorder** = `accent` at 28% alpha - subtle accent borders

### State colors

| Key | Where it appears |
|-----|-----------------|
| `caution` | BPM pill during playback, edit-mode indicator, metric-modulation item text |
| `danger` | Stop button, delete confirmations, error text |

Each also gets derived `*Bg` (12% alpha) and `*Border` (28% alpha) variants.

### Track controls

| Key | Where it appears |
|-----|-----------------|
| `muteColor` | M chip when muted |
| `soloColor` | S chip when soloed |

### Beat item colors

| Key | Where it appears |
|-----|-----------------|
| `beatActiveBg` | Normal (non-rest, non-selected) beat background |
| `beatActiveBorder` | Normal beat border |
| `beatSelectedBg` | Beat background when the cursor is on it |
| `beatSelectedBorder` | Beat border when selected |
| `beatSelectedText` | Beat numerator text when selected |

### Non-beat track items

| Key | Where it appears |
|-----|-----------------|
| `bracketText` | `[` and `]` bracket glyph color |
| `repeatText` | `×N` repeat count color |
| `setBpmText` | `=bpm` tempo-change item color |

When unselected, these will have 55% alpha.

### Other

| Key | Type | Where it appears |
|-----|------|-----------------|
| `trackActiveBg` | color | Entire track row background when that track is selected |
| `deleteText` | color | `×` delete glyph in project list |
| `thumbColor` | color | Toggle thumb and volume slider handle |
| `defaultPanelAlpha` | float 0–1 | Opacity of the bottom edit panel background overlay |