# OffGridBites
# 📡 OffGrid Bites (`com.offgrid.btfood`)

**Zero-GPS, Zero-Internet, Peer-to-Peer Bluetooth Food Delivery for Android**

**OffGrid Bites** is an ultra-lightweight (~80 KB), local-first Android application designed for grid-down scenarios, remote campgrounds, disaster relief zones, festivals, and hyper-local community kitchens. It enables customers and vendors/couriers to dispatch food orders and locate drop-off zones entirely over **Bluetooth Classic (RFCOMM)** and **RSSI signal strength**—requiring **zero cellular data, zero Wi-Fi routers, and zero GPS satellites**.

---

## ⚡ Key Features

* **100% Off-Grid RFCOMM Dispatch:** Orders are serialized into compact JSON payloads and beamed directly from the customer's device to a vendor/kitchen node over raw Bluetooth sockets (`8ce255c0-200a-11e0-ac64-0800200c9a66`).
* **No-GPS Proximity Radar (RSSI Homing):** Instead of satellite coordinates, users input a visual **Drop Landmark** (e.g., *"Camp Sector B - Blue Tarp"* or *"Porch #4"*). During discovery scans, the app reads real-time **Received Signal Strength Indicator (RSSI)** values in `dBm` and translates them into human-readable distance zones:
  * **Immediate:** `>= -55 dBm` (Less than 2 meters)
  * **Near:** `-56 to -72 dBm` (2 to 10 meters)
  * **Approaching:** `-73 to -85 dBm` (10 to 25 meters)
  * **Far:** `< -85 dBm` (Greater than 25 meters)
* **True Zero-Location Permission Architecture:** Uses Android 12+ (API 31+) `neverForLocation` permission flags (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`), allowing the app to scan and connect with system Location/GPS completely turned off on modern Android devices.
* **Viral Self-Replicating APK ("Beam APK"):** Built-in APK extractor reads the app's own installed binary (`applicationInfo.sourceDir`) and beams the installer directly to nearby phones over Bluetooth or offline Quick Share—allowing a single seed phone to distribute the app across an entire camp or neighborhood without internet access.
* **Ultra-Tiny Footprint (~80 KB):** Built with pure Android SDK classes and programmatic views (`android.app.Activity`, `LinearLayout`) with **zero external library dependencies**. Transfers over slow Bluetooth Classic connections in less than 2 seconds.
* **Dual-Role Single Binary:** Every user installs the exact same APK and can dynamically act as either a **Customer Node** or a **Vendor/Courier Server Node**.

---

## 📱 How to Use OffGrid Bites in the Field

### 1. Seeding the App to Other Phones (Offline)
1. Open **OffGrid Bites** on the primary ("seed") phone.
2. Tap **"Beam APK"** in the top-right corner.
3. Select **Bluetooth** (or offline **Quick Share**) from the system share sheet and choose the recipient's phone.
4. On the receiving phone, accept the file transfer and tap `OffGridBites.apk` to install (enable *Install unknown apps* if prompted).

### 2. Setting Up a Kitchen / Vendor Node
1. Open the app on the kitchen or courier phone.
2. Tap **"Make Visible"** to allow nearby customer phones to discover your Bluetooth radio for 300 seconds.
3. Tap **"Start Vendor Server"**. The status bar will update to:
   > `Status: VENDOR SERVER ACTIVE (Listening on RFCOMM)`
4. Incoming orders will automatically pop up at the bottom of the screen under **INCOMING VENDOR ORDERS** with the customer's callsign, hardware MAC address, visual drop landmark, itemized order, and total.

### 3. Placing an Order (Customer Node)
1. Enter your **Name / Callsign** (e.g., `Unit-01`) and your **Drop Landmark** (e.g., `Camp Sector B - Blue Tarp`).
2. Use the **`+`** and **`-`** buttons to add items to your cart.
3. Tap **"Scan Nodes"** to search for nearby Bluetooth devices and measure their live RSSI distance.
4. Tap the desired **Vendor Node** button in the scanned list to immediately beam your order over Bluetooth.

---

## 🛠️ Repository Structure

```text
OffGridBites/
├── .github/
│   └── workflows/
│       └── build-apk.yml          # Cloud CI workflow to compile & publish OffGridBites.apk
├── app/
│   ├── build.gradle               # Zero-dependency Android module configuration
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # Bluetooth permissions with neverForLocation flag
│           └── java/com/offgrid/btfood/
│               └── MainActivity.kt # Complete UI, RFCOMM server/client, & APK beamer
├── build.gradle                   # Root Gradle plugin definitions
└── settings.gradle                # Project settings & repository declarations
```

---

## 🚀 Building the APK

### Option A: Automated Cloud Build (GitHub Actions)
Every push to the `main` or `master` branch automatically triggers the `.github/workflows/build-apk.yml` pipeline:
1. Spins up an Ubuntu runner with **JDK 17** and **Gradle 8.7**.
2. Accepts Android SDK licenses and executes `gradle :app:assembleDebug`.
3. Uploads the compiled binary as a downloadable workflow artifact (`OffGridBites-APK`) **and** commits `OffGridBites.apk` directly to the repository root for one-tap mobile downloads.

### Option B: Local Terminal Build
If you have the Android SDK installed locally, clone the repository and run:

```bash
git clone [https://github.com/YOUR_USERNAME/OffGridBites.git](https://github.com/YOUR_USERNAME/OffGridBites.git)
cd OffGridBites
gradle :app:assembleDebug
```

The compiled installer will be output to:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔧 Technical Specifications

| Parameter | Specification |
| :--- | :--- |
| **Package Name** | `com.offgrid.btfood` |
| **Min SDK** | API 24 (Android 7.0 Nougat) |
| **Target / Compile SDK** | API 34 (Android 14) |
| **Transport Protocol** | Bluetooth Classic RFCOMM (`createInsecureRfcommSocketToServiceRecord`) |
| **Service UUID** | `8ce255c0-200a-11e0-ac64-0800200c9a66` |
| **Location Requirement** | None (`neverForLocation` on API 31+; RSSI + Landmark text routing) |
| **External Dependencies** | `0` (Pure Android Framework SDK) |

---

## 📜 License

Released as open-source software for community resilience, off-grid experimentation, and educational use. Feel free to fork, customize the menu array in `MainActivity.kt`, and adapt for local mutual aid networks.

