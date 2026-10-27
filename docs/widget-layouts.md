# Widget layouts

The widget declares a default size of **2×1** and a minimum of **1×1**. On Android 12 and later, the default comes from `targetCellWidth`/`targetCellHeight`. On older versions it comes from `minWidth`/`minHeight` (110×40dp). You'll find these in `app/src/main/res/xml/usage_widget_info.xml`.

`UsageWidget` uses `SizeMode.Responsive`. Glance renders the **largest breakpoint that fits** the widget's current size:

| Breakpoint | Size | Usually | Composable |
|---|---|---|---|
| `TINY` | 40×40dp | 1×1 | `Tiny` |
| `SHORT` | 100×40dp | 2×1 on launchers with short rows (< 80dp tall) | `Short` |
| `STACK` | 100×80dp | 2×1, 3×1, 4×1 | `Stacked(tall = false)` |
| `STACK_TALL` | 100×150dp | 2×2 and larger | `Stacked(tall = true)` |

A width under `NARROW_WIDTH` (150dp) shortens the reset text from "resets 2h 13m" to "2h 13m", to leave room for the corner button.

## What each layout shows

**1×1 (`Tiny`):** the session % in its usage color, with the weekly % underneath. There's no room for a button, so **tapping the tile refreshes**. Every other layout opens the app on tap.
```
 15%
wk 11%
```

**Stacked (`STACK`):** a label, then the bar with its % on the right, then the reset time. The corner shows how long ago the data was fetched, plus ↻.
```
Session
████████░░░░░░░░  42%
resets 2h 13m
Weekly
███░░░░░░░░░░░░░  18%
resets Thu 09:00   4m ↻
```

**Stacked tall (`STACK_TALL`):** the same as Stacked, plus extra weekly limits (Opus or Sonnet) when the API reports them. The corner reads "Updated 4m".

**Short rows (`SHORT`):** three lines per limit don't fit, so the reset time moves onto the label line, and ↻ sits at the end of the last bar.
```
Session        2h 13m
████████░░░░░  42%
Weekly      Thu 09:00
███░░░░░░░░░░  18% ↻
```

## States

| State | What the widget shows |
|---|---|
| Signed out / no data yet | "Tap to sign in" ("Sign in" when small). Tapping opens the app. |
| Refreshing | ↻ becomes **…** (1×1: the percentage becomes …) |
| Last refresh failed | ↻ becomes a red **!**. The last good numbers stay. |
| Reset more than 24 h away | Weekday and local time, e.g. "Thu 09:00" (`Format.resets`) |

## Changing a layout

- Text sizes are deliberately small (8–9sp) so the stacked layout fits a single row. Check the height budget before you increase anything: one limit block is about 35dp, two blocks plus padding must fit in 80dp.
- Glance supports only a subset of Compose layouts. There's no `weight` on arbitrary children (use `GlanceModifier.defaultWeight()` inside a `Row` or `Column`), and no custom drawing. Bars are `LinearProgressIndicator`.
- Keep the **app preview** (`MainActivity.WidgetPreview`) and the **picker preview** (`res/layout/widget_preview.xml`) matching the real layout.
- There are no automated UI tests for the widget, so test on a device at 1×1, 2×1 and 2×2 at least.
