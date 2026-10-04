# MirzaDev-UniversalAuthCenter

UniversalAuthCenter adlh library authentication + authorization untuk Android yang dibuat biar bisa dipakai secara standalone dan disisipin ke APK target tanpa harus memakai Android Studio, Gradle, AndroidX, atau dependency tambahan.

Output utamanya adalah:

```text
out/classes.dex
```

DEX ini bisa dimasukkan ke APK target sebagai dex tambahan.

## Buat apa?

Flow dasarnya:

```text
App dibuka
    ↓
AuthCenter.start(Activity)
    ↓
Login UI
    ↓
Firebase Authentication
    ↓
dapat Firebase ID Token
    ↓
Authorization Backend
    ↓
cek user + akses aplikasi
    ↓
AUTHORIZED
    ↓
lanjut ke aplikasi
```

Jadi authentication dan authorization dipisahkan.

**Authentication** menjawab:

> "Siapa user ini?"

**Authorization** menjawab:

> "User ini boleh memakai aplikasi yang mana?"

## Kenapa standalone?

Project ini sengaja dibuat sesederhana mungkin untuk deployment ke APK existing.

Tidak menggunakan:

* Gradle
* Android Studio
* AndroidX
* Jetpack Compose
* Firebase Android SDK
* AAR
* dependency library eksternal untuk sisi Android

Android client menggunakan Java standar + Android SDK API yang diperlukan.

Backend B4 berjalan terpisah di Vercel dan menggunakan Firebase Admin SDK.

---

## Struktur project

```text
MirzaDev-UniversalAuthCenter/
│
├── src/
│   └── com/universal/authcenter/
│       ├── AuthCenter.java
│       ├── AuthConfig.java
│       ├── AuthCallback.java
│       ├── AuthResult.java
│       ├── AuthenticationCallback.java
│       │
│       ├── auth/
│       │   ├── AuthState.java
│       │   ├── AuthorizationClient.java
│       │   ├── FirebaseAuthClient.java
│       │   ├── SessionManager.java
│       │   └── TokenManager.java
│       │
│       └── ui/
│           └── LoginDialog.java
│
├── auth-backend/
│   ├── api/
│   │   └── auth/
│   │       └── authorize.ts
│   ├── src/
│   │   ├── config/
│   │   ├── firebase/
│   │   ├── services/
│   │   └── utils/
│   ├── package.json
│   ├── package-lock.json
│   ├── tsconfig.json
│   └── .env.example
│
├── config/
│   └── app.properties
│
├── scripts/
│   └── build.ps1
│
└── README.md
```

## Requirements

Untuk build Android client:

* JDK 21+
* Android SDK
* Android platform `android-37.0`
* Android Build Tools `36.0.0`
* D8

Build script sekarang menggunakan:

```text
android.jar
    Android SDK 37

d8
    Build Tools 36.0.0
```

## Build DEX

Konfigurasi target berada di:

```text
config/app.properties
```

Contohnya:

```properties
appKey=cracking-exam
firebaseApiKey=YOUR_FIREBASE_CLIENT_API_KEY
```

`appKey` adalah identifier internal aplikasi.

Jangan membuat `appKey` dari package name.

`firebaseApiKey` adalah Firebase client API key. Ini bukan Firebase Admin private key.

Setelah konfigurasi siap, jalankan:

```powershell
.\scripts\build.ps1
```

Pipeline build:

```text
config/app.properties
        ↓
GeneratedConfig.java
        ↓
javac
        ↓
.class
        ↓
D8
        ↓
out/classes.dex
```

`GeneratedConfig.java` dibuat otomatis saat build dan tidak perlu diedit manual.

## Menggunakan DEX di APK target

DEX hasil build:

```text
out/classes.dex
```

Masukkan DEX tersebut sebagai dex tambahan ke APK target.

Nama file dex tidak harus `classes.dex`.

Contohnya, kalau APK target sudah punya:

```text
classes.dex
classes2.dex
```

maka DEX UniversalAuthCenter bisa menjadi:

```text
classes3.dex
```

Nomor dex mengikuti urutan dex yang sudah ada.

## Memanggil AuthCenter

Setelah DEX dimasukkan ke APK target, integrasi minimalnya:

```smali
invoke-static {p0}, Lcom/universal/authcenter/AuthCenter;->start(Landroid/app/Activity;)V
```

Dengan default configuration, `AuthCenter.start(Activity)` menggunakan konfigurasi yang sudah ditanam saat build:

```text
GeneratedConfig.APP_KEY
GeneratedConfig.FIREBASE_API_KEY
```

Jadi APK tidak perlu membaca `app.properties` saat runtime.

## Custom configuration

Untuk integrasi yang membutuhkan konfigurasi eksplisit, `AuthConfig` tetap menyediakan constructor dengan parameter:

```java
new AuthConfig(
    appId,
    appName,
    backendUrl,
    firebaseApiKey,
    appKey
);
```

Dengan cara ini, default build-time config tetap tersedia tetapi integrator masih punya opsi override.

## Backend authorization

Backend B4 menyediakan endpoint:

```text
POST /api/auth/authorize
```

Production backend saat ini:

```text
https://mirzadev-universalauthcenter.vercel.app/
```

Request:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
Content-Type: application/json
```

Body:

```json
{
  "appKey": "cracking-exam"
}
```

Backend kemudian:

1. memverifikasi Firebase ID token
2. mengambil UID dari token yang sudah diverifikasi
3. mengecek `/users/{uid}`
4. mengecek status `active`
5. mengecek `expiresAt`
6. mengecek `/appAccess/{appKey}/{uid}`
7. menentukan apakah akses diberikan

UID, email, `active`, dan `expiresAt` dari request client tidak dipercaya.

Untuk memeriksa source backend secara lokal, gunakan Node.js `24.x`:

```powershell
cd auth-backend
npm ci
npm run typecheck
```

## Firebase

Struktur authorization yang digunakan:

```text
/users/{uid}

/appAccess/{appKey}/{uid}
```

Contoh:

```text
/users/USER_UID
    active: true
    expiresAt: 1799999999999

/appAccess/cracking-exam/USER_UID
    active: true
```

`expiresAt` menggunakan Unix epoch dalam milliseconds.

Device binding belum menjadi bagian dari B4.

## Response authorization

Akses berhasil:

```json
{
  "status": "AUTHORIZED",
  "message": "Access granted"
}
```

Beberapa status denial/error:

```text
BAD_REQUEST
UNAUTHENTICATED
INVALID_TOKEN
USER_DISABLED
USER_EXPIRED
ACCESS_DENIED
UNKNOWN_APP
METHOD_NOT_ALLOWED
AUTHORIZATION_UNAVAILABLE
INTERNAL_ERROR
```

## Security

Beberapa hal penting:

### Yang boleh ada di client

* Firebase client API key
* `appKey`
* backend URL

### Yang tidak boleh masuk client/repository

* Firebase Admin private key
* Firebase service account JSON
* `FIREBASE_CLIENT_EMAIL` untuk backend
* Vercel secrets
* credential backend lainnya

Credential Firebase Admin hanya disimpan sebagai Environment Variables di backend.

## Status project

```text
B1  Core Foundation       
B2  Session & Identity    
B3  Firebase Auth         
B4  Authorization         
B5  Login UI              
B6  Device Binding        
B7  Universal APK         
B8  DEX Tooling           
B9  AdminCenter           
B10 Hardening & Release   
```

Project ini masih terus dikembangkan. Struktur dan API bisa berubah pada batch berikutnya, tetapi sebisa mungkin perubahan dijaga tetap backward-compatible.

## Catatan

UniversalAuthCenter dibuat sebagai project modular supaya authentication, authorization, UI, session, dan tooling tidak tercampur dalam satu class besar.

Target akhirnya adalah menghasilkan authentication center yang bisa dipakai ulang
