# Better Sounds (Fabric, Minecraft Java 26.3)

Port tidak resmi dari **Better Sounds** (Bedrock) oleh **MSAB14** ke Minecraft Java.
Dibuat dengan izin tertulis dari MSAB14, dengan syarat namanya dicantumkan di kredit.
Pack asli: https://www.curseforge.com/minecraft-bedrock/texture-packs/better-sounds

## Fitur
- Seluruh suara Better Sounds untuk Java (blok, mob, item, cuaca, dll.).
- Suara saat melempar (drop) item.
- Layar pengaturan per suara: ketik `/bettersounds` di game.
  - Volume (0-100%) dan pitch (0.5x-2.0x) tiap suara.
  - Pilihan suara: Default (Better Sounds) atau file .ogg buatanmu sendiri.
  - Impor: drag & drop file .ogg (Vorbis) ke jendela game saat layar pengaturan terbuka
    (atau taruh di `config/bettersounds/library/`).
  - Klik **Apply** untuk menyimpan, membuat resource pack `BetterSounds-Config`, dan me-reload suara.

## Build
`sounds-assets.zip` diekstrak ke `src/main/resources` (otomatis di GitHub Actions), lalu `gradle build` (JDK 25).

## Kredit
Suara: MSAB14 (Better Sounds). Semua hak atas suara tetap milik MSAB14.
