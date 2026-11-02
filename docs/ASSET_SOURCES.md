# Visual assets and selected reference

User-selected UI authority (2026-11-02): C:/Users/jeffw/Downloads/UmbraCam Neon Recording Suite.png. This is a design reference, not a fabricated camera/screen preview or a shipping screenshot asset. The user explicitly requested this redesign in addition to the ongoing thermal hardening task.

The existing blue/silver UmbraCam eye at app/src/main/res/drawable-nodpi/umbracam_icon.png remains the user-provided launcher/brand artwork. Native thumbnails are generated from actual completed local recordings; no sample faces/city images are substituted for live capture or the user's media.

Poppins Regular and SemiBold are bundled locally from the official Google Fonts project, with SIL Open Font License text at app/src/main/assets/licenses/Poppins-OFL.txt. They keep the selected geometric typography consistent across OEM system-font choices; they add no runtime/network dependency.

- https://github.com/google/fonts/blob/main/ofl/poppins/Poppins-Regular.ttf — SHA2567e65201e9b79159e2300267cc885e16c8dcef2424cdfa09a29bfb0980a94a7ba
- https://github.com/google/fonts/blob/main/ofl/poppins/Poppins-SemiBold.ttf — SHA256d3bf1bdaf0550e83da9ac0b1d1d9fe6db086835a83aa28578e609a394b9a0286
- https://github.com/google/fonts/blob/main/ofl/poppins/OFL.txt

The ic_suite_*.xml line icons and Compose gradient/curve treatments were authored as native code for this app. No third-party icon package or generated raster illustration was added. The old Material system description is retained in .impeccable/history/DESIGN.material-2026-11-01.md; the new approved system must be documented from the final code and rendered evidence.
