# NFC 工具箱 — Android design system

Native Android utility, Kotlin + Jetpack Compose + Material 3. The ui-ux-pro-max
search matched Minimalism & Swiss Style and the mobile utility palette. The
returned landing-page pattern does not fit a native utility: use four bottom
destinations (读取 / 写入 / 卡包 / 工具 / 记录). Version 0.1.0 replaces the large
scan illustration with a compact status panel so common actions fit on screen.

- Primary #2563EB, background #F6F8FC, surface #FFFFFF, ink #17243D.
- Secondary text #53627B; destructive #B3261E. Dark colors live in Theme.kt.
- System sans for Chinese and body; monospace for UID, protocols and byte data.
- 4/8 dp spacing rhythm; 20 dp phone gutters, max 720 dp content on tablets.
- 24 dp outline Material icons, 48 dp minimum targets, 20–28 dp card corners.
- Every state has a text label; no animated decoration or infinite scan pulse.
- All screens scroll, respect system bars, support dark mode and font scaling.
- Erase, format and permanent locking require a dialog, then one card tap.
- Normal writing uses an explicit fixed bottom button and the physical card tap;
  the overwrite explanation remains next to the button without another modal.
- Common entry types are inline; more types are disclosed on demand. System
  pickers and clipboard recognition prevent redundant typing. Unsaved input is
  retained across tabs and app restarts. Hide bottom navigation when IME is open.
- Always show actual hardware state. Sample cards are explicitly labelled.
- NFC I/O and bounded file reads run off the main thread. No network permission.
