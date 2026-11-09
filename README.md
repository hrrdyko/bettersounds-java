# Better Sounds (Fabric, Minecraft Java 26.3)

Port tidak resmi dari **Better Sounds** (Bedrock) oleh **MSAB14** ke Minecraft Java.
Dibuat dengan izin tertulis dari MSAB14, dengan syarat namanya dicantumkan di kredit.
Pack asli: https://www.curseforge.com/minecraft-bedrock/texture-packs/better-sounds

## Fitur
- Seluruh suara Better Sounds untuk Java (blok, mob, item, cuaca, dll.).
- Suara saat melempar (drop) item.
- Suara UI baru: scroll hotbar, scroll slider, hover tombol, buka/tutup inventory/chat/menu, masuk dunia/server, mengetik/menghapus/mengirim chat.
- Suara ayunan (swing) per jenis alat/senjata (pedang, kapak, pickaxe, sekop, cangkul, trident, mace, tangan kosong, item lain) dan suara pelan saat item dipilih di hotbar (pedang, kapak, blok, armor, dst.). Semua bisa diatur di tab Item dan GUI.
- Volume per event bisa sampai 500% (suara klik tombol vanilla hanya diputar 25%).
- Pengaturan untuk SEMUA event suara di game (vanilla dan mod lain), dikelompokkan: GUI, Block, Entity, Item, World, Mods.
- Layar pengaturan per suara: tekan **K** (bisa diubah di Controls), ketik `/bettersounds`, atau klik tombol config di **Mod Menu**.
  - Volume (0-100%) dan pitch (0.5x-2.0x) tiap suara.
  - Pilihan suara: Default (Better Sounds) atau file .ogg buatanmu sendiri.
  - Impor: drag & drop file .ogg (Vorbis) ke jendela game saat layar pengaturan terbuka
    (atau taruh di `config/bettersounds/library/`).
  - Klik **Apply** untuk menyimpan dan me-reload suara. Semuanya tersimpan di `config/bettersounds/` dan dimuat otomatis oleh mod, tanpa resource pack terpisah.

## Library & editor
- Impor suara sendiri lewat tombol **Files** / **Folder** di halaman Library, atau drag & drop file **.ogg, .mp3, .wav**, folder, atau **.zip** ke jendela game.
- Folder atau .zip berisi banyak suara menjadi **grup**: saat dipilih untuk suatu event, variannya diputar acak (seperti suara stone bawaan).
- Dropdown tiap event punya pilihan **Default** (Better Sounds), **Vanilla** (suara asli Minecraft), atau item Library. Tombol **Set all shown** mengubah semua baris hasil pencarian sekaligus.
- Di halaman **Library**, klik **Edit** untuk memotong (trim), mengubah kecepatan (0.25x-4x), dan volume (0-1000%) langsung di dalam game.
- Klik **Apply** supaya suara hasil edit dipakai game (dirender ke `config/bettersounds/rendered/`).

## Build
`sounds-assets.zip` diekstrak ke `src/main/resources` (otomatis di GitHub Actions), lalu `gradle build` (JDK 25).

## Kredit
Decoder MP3: JLayer (LGPL) dan dialog file lwjgl-tinyfd, dibundel sebagai jar terpisah (jar-in-jar).
Suara: MSAB14 (Better Sounds). Semua hak atas suara tetap milik MSAB14.
