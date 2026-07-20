# Ysmayylov Blur Bottom Nav

A modern, **floating glassmorphism bottom navigation** for classic Android **XML** projects — with a
**blur engine written completely from scratch**. No BlurView, no RenderScript wrappers, no external
blur dependency of any kind.

> Package: `com.ysmayylov.blur.bottomnav` · Language: **Kotlin** · Views: **XML (no Compose)** · minSdk **21**

---

## ✨ Features

- **Real-time "blur behind" backdrop** — blurs whatever scrolls underneath the bar, live.
- **Self-contained blur engine** (zero third-party blur libs):
  - **Android 12+ (API 31):** native GPU blur via `RenderEffect` + `HardwareRenderer` + `ImageReader`.
  - **Below Android 12:** hand-written **Stack Blur** (default) and a separable **Gaussian Blur**.
  - **Automatic fallback** from GPU → CPU if a device's GPU path misbehaves.
- **Glassmorphism styling:** tint, background alpha, gradient sheen, rounded corners, border, shadow/elevation.
- **Navigation items:** icon + title, selected/unselected states, numeric **badges** and **dot badges**, per-item click callbacks.
- **Animations:** spring-like selected scale, icon lift, `ArgbEvaluator` color transitions, a **floating selected indicator**, and ripple feedback.
- **Performance-minded:** heavy work runs on a down-scaled bitmap, throttled by an update interval, with bitmap caching, recycling and no leaks.
- **Lifecycle-safe:** attaches/detaches with the window, survives rotation.
- **Light / dark theme** support with day-night color resources, plus full runtime color configuration.

---

## 📦 Installation (JitPack)

**1.** Add JitPack to your root `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**2.** Add the dependency in your app module `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.YsmayylovMerdan:YsmayylovBlurBottomNav:1.0.0'
}
```

_(Replace the tag with the release you published on GitHub.)_

---

## 🧩 XML usage

Place the view **on top of** your scrolling content (e.g. inside a `CoordinatorLayout` or `FrameLayout`)
so there is something behind it to blur:

```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <com.ysmayylov.blur.bottomnav.YsmayylovBlurBottomNavView
        android:id="@+id/bottomNav"
        android:layout_width="match_parent"
        android:layout_height="72dp"
        android:layout_gravity="bottom"
        android:layout_marginStart="16dp"
        android:layout_marginEnd="16dp"
        android:layout_marginBottom="20dp"
        app:blurEnabled="true"
        app:blurRadius="24dp"
        app:cornerRadius="36dp"
        app:tintColor="#40FFFFFF"
        app:borderColor="#40FFFFFF"
        app:borderWidth="1dp"
        app:selectedColor="#FFFFFFFF"
        app:unSelectedColor="#B3FFFFFF"
        app:indicatorColor="#33FFFFFF"
        app:animationDuration="320" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---

## 🛠 Kotlin usage

```kotlin
val bottomNav = findViewById<YsmayylovBlurBottomNavView>(R.id.bottomNav)

val items = listOf(
    NavItem(id = "home",     icon = R.drawable.ic_home,     title = "Home"),
    NavItem(id = "search",   icon = R.drawable.ic_search,   title = "Search"),
    NavItem(id = "favorite", icon = R.drawable.ic_favorite, title = "Saved", badgeCount = 3),
    NavItem(id = "profile",  icon = R.drawable.ic_profile,  title = "Profile", badgeDot = true)
)

bottomNav.setItems(items)

bottomNav.setOnItemSelectedListener { id ->
    when (id) {
        "home"     -> { /* show home */ }
        "search"   -> { /* show search */ }
        "favorite" -> bottomNav.setBadgeCount("favorite", 0) // clear badge on open
        "profile"  -> { /* show profile */ }
    }
}
```

---

## 🎨 Customization

### All XML attributes

| Attribute                | Type       | Purpose                                            |
|--------------------------|------------|----------------------------------------------------|
| `blurEnabled`            | boolean    | Turn the live blur backdrop on/off                 |
| `blurRadius`             | dimension  | Blur strength                                       |
| `blurUpdateInterval`     | integer    | Min ms between blur recomputes (0 = every frame)   |
| `cornerRadius`           | dimension  | Rounded-corner radius                               |
| `backgroundColor`        | color      | Solid color under the tint (usually transparent)   |
| `backgroundAlpha`        | float      | Alpha applied to `backgroundColor`                 |
| `tintColor`              | color      | Frosted-glass tint painted over the blur           |
| `borderColor`            | color      | Border stroke color                                 |
| `borderWidth`            | dimension  | Border stroke width                                 |
| `shadowEnabled`          | boolean    | Elevation shadow on/off                             |
| `gradientOverlayEnabled` | boolean    | Subtle top-down glass sheen                         |
| `selectedColor`          | color      | Selected item icon/label color                      |
| `unSelectedColor`        | color      | Unselected item icon/label color                    |
| `indicatorColor`         | color      | Floating selected-indicator color                   |
| `iconSize`               | dimension  | Item icon size                                       |
| `titleSize`              | dimension  | Item label text size                                |
| `itemSpacing`            | dimension  | Spacing between items                               |
| `animationDuration`      | integer    | Selection/indicator animation duration (ms)         |
| `badgeColor`             | color      | Badge circle color                                  |
| `badgeTextColor`         | color      | Badge number color                                  |

### Runtime API

```kotlin
bottomNav.setSelectedItem("profile")          // or setSelectedIndex(3)
bottomNav.setBadgeCount("favorite", 5)
bottomNav.setBadgeDot("profile", true)

bottomNav.setBlurEnabled(true)
bottomNav.setBlurRadiusDp(30f)
bottomNav.setCornerRadiusDp(40f)
bottomNav.setTintColor(0x66FFFFFF)

// Swap the whole palette in one call (great for theme toggles)
bottomNav.applyColors(
    selected  = Color.WHITE,
    unselected = 0x99FFFFFF.toInt(),
    tint      = 0x40202124,
    border    = 0x33FFFFFF,
    indicator = 0x33FFFFFF
)
```

---

## 🧠 How the blur engine works

```
blur/
 ├── BlurEngine.kt        # common contract
 ├── RenderEffectBlur.kt  # API 31+ GPU pipeline (RenderEffect + HardwareRenderer + ImageReader)
 ├── GaussianBlur.kt      # separable Gaussian (2 × O(r) passes)
 ├── StackBlur.kt         # Mario Klingemann Stack Blur (default CPU fallback)
 └── BitmapProcessor.kt   # down-scaled capture buffer lifecycle

controller/
 ├── BlurController.kt        # strategy contract
 └── PreDrawBlurController.kt # capture-behind + blur + draw, with self-capture guard
```

On every pre-draw pass the controller draws the content behind the bar into a **heavily down-scaled
bitmap**, blurs it with the active engine, and paints it back — clipped to the rounded rect via a
`BitmapShader`. Drawing the bar into its own capture buffer is skipped to prevent an infinite
self-capture loop.

---

## ⚡ Performance notes

- Blur runs on a **down-scaled** bitmap (≈4× GPU, ≈8× CPU) — cheap even on the CPU path.
- Use `app:blurUpdateInterval="16"` (or higher) to cap recompute frequency on lower-end devices.
- Works inside `Fragment`, over `RecyclerView`, and within `CoordinatorLayout`.
- Bitmaps are cached and reused; buffers are recycled and GPU resources released on detach.

---

## 📸 Screenshots

<!-- Add screenshots / GIFs here -->
| Light | Dark |
|-------|------|
| _screenshot_ | _screenshot_ |

---

## 🗺 Project structure

```
YsmayylovBlurBottomNav/
├── library/   # the publishable AAR module (com.ysmayylov.blur.bottomnav)
├── sample/    # demo app showing the bar over a scrolling list
├── README.md
└── LICENSE
```

---

## 📄 License

MIT © 2026 **Ysmayylov Merdan**. See [LICENSE](LICENSE).
