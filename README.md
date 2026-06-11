# EnerShip Travel — APK Android (HC-06 Bluetooth Classic)

Application Android native WebView qui communique avec le HC-06
via **Bluetooth Classic SPP** (RFCOMM UUID 00001101-...).

---

## Architecture

```
HTML/JS (assets/www/index.html)
        ↕  JavascriptInterface "AndroidBT"
MainActivity.java
        ↕  BluetoothSocket (RFCOMM)
HC-06 → Arduino Mega 2560
```

Le pont Java `AndroidBTInterface` expose 3 méthodes à JavaScript :
- `AndroidBT.getPairedDevices()` → JSON des appareils jumelés
- `AndroidBT.connect(address)` → connexion RFCOMM
- `AndroidBT.send(data)` → envoi d'une commande

Et JavaScript expose 2 callbacks appelés par Java :
- `window.onAndroidBTData(line)` → réception d'une trame `$DATA`/`$ALARM`
- `window.onAndroidBTDisconnected(reason)` → déconnexion

---

## Prérequis

- **Android Studio** (recommandé) **ou** JDK 17 + Android SDK command-line tools
- Android SDK Platform 34
- Build Tools 34.x

---

## Méthode 1 — Android Studio (le plus simple)

1. Ouvrez Android Studio
2. **File → Open** → sélectionnez ce dossier `enship-android/`
3. Attendez la sync Gradle (télécharge les dépendances automatiquement)
4. **Build → Build Bundle(s)/APK(s) → Build APK(s)**
5. L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`
6. Transférez l'APK sur votre Android et installez-le
   (activez "Sources inconnues" dans Paramètres → Sécurité)

---

## Méthode 2 — GitHub Actions (sans rien installer)

1. Créez un repository GitHub (public ou privé)
2. Poussez ce dossier : `git init && git add . && git commit -m "init" && git push`
3. GitHub Actions lance le build automatiquement
4. Dans l'onglet **Actions** de votre repo → cliquez sur le dernier workflow
5. Téléchargez l'artefact **EnerShip-debug** (fichier .zip contenant l'APK)

---

## Méthode 3 — Ligne de commande

```bash
# 1. Pointer vers votre SDK dans local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 2. Générer le wrapper Gradle
gradle wrapper --gradle-version=8.4

# 3. Builder
./gradlew assembleDebug

# APK : app/build/outputs/apk/debug/app-debug.apk
```

---

## Utilisation de l'APK

1. **Jumelez d'abord le HC-06** dans les paramètres Bluetooth Android
   (code PIN par défaut : `1234` ou `0000`)
2. Lancez EnerShip Travel
3. Appuyez sur l'indicateur Bluetooth en haut à droite
4. La liste des appareils jumelés s'affiche — sélectionnez `HC-06` ou `EnerShip_BT`
5. La connexion s'établit et les données arrivent en temps réel

---

## Permissions Android requises

| Permission | Utilité |
|---|---|
| `BLUETOOTH_CONNECT` (Android 12+) | Connexion RFCOMM |
| `BLUETOOTH_SCAN` (Android 12+) | Découverte |
| `BLUETOOTH` + `BLUETOOTH_ADMIN` (≤Android 11) | Idem |
| `ACCESS_FINE_LOCATION` (≤Android 11) | Requis par Android pour BT |

---

## Structure du projet

```
enship-android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/www/
│   │   │   └── index.html          ← Votre app HTML (patché)
│   │   ├── java/com/enship/travel/
│   │   │   └── MainActivity.java   ← Bridge BT Classic ↔ WebView
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       └── values/styles.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
└── .github/workflows/build.yml     ← Build cloud GitHub Actions
```
