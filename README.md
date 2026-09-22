# Time Controller

Aplikasi Android Timer & Stopwatch dengan notifikasi persisten di status bar (24 jam), popup pengaturan cepat, dan preset durasi kustom.

## Cara upload ke GitHub dan build APK

1. Buat repository baru di github.com (public atau private, bebas).
2. Di komputer/HP kamu (Termux juga bisa), jalankan di dalam folder project ini:
   ```
   git init
   git add .
   git commit -m "Initial commit - Time Controller"
   git branch -M main
   git remote add origin https://github.com/USERNAME/NAMA_REPO.git
   git push -u origin main
   ```
3. Setelah push berhasil, buka tab **Actions** di repo GitHub kamu. Workflow "Build APK" akan otomatis jalan.
4. Tunggu sampai selesai (tanda centang hijau), lalu buka hasil run tersebut.
5. Scroll ke bagian **Artifacts**, unduh file `time-controller-debug-apk` — itu isinya `app-debug.apk` yang siap diinstall di HP Android.

## Catatan instalasi APK

Karena APK ini debug build (belum ditandatangani untuk Play Store), saat instal di HP kamu perlu mengizinkan "Install dari sumber tidak dikenal" di pengaturan Android.

## Struktur fungsi utama

- `TimeService.kt` — foreground service yang menjalankan timer/stopwatch dan notifikasi 24 jam.
- `MainActivity.kt` — halaman utama app, termasuk kelola preset kustom.
- `SettingsPopupActivity.kt` — popup pengaturan cepat (durasi, switch mode, pilih preset) yang bisa dibuka dari tombol "Atur" di notifikasi.
- `PresetStore.kt` — penyimpanan preset durasi secara lokal di HP.
