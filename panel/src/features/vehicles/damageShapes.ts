// Hasar semasindaki ustten arac cizimi (DamageMap ve sahibinden icin uretilen hasar resmi ortak kullanir).
// viewBox 0 0 220 400, on taraf yukarida, sol taraf aracin sol yani. 13 boyanabilir parca (sahibinden / arabam.com ile ayni);
// tekerlekler, aynalar ve camlar sadece gorunum icindir, durumlari yoktur.
import type { BodyPart } from "../../shared/types";

export const SHAPE: Record<BodyPart, string> = {
  // Orta hat: on tampon -> kaput -> (on cam) -> tavan -> (arka cam) -> bagaj -> arka tampon
  FRONT_BUMPER: "M33 42 C35 20 68 8 110 8 C152 8 185 20 187 42 Z",
  HOOD: "M57 46 L163 46 C165 70 165 98 162 122 L58 122 C55 98 55 70 57 46 Z",
  ROOF: "M68 176 Q110 170 152 176 L153 262 Q110 268 67 262 Z",
  TRUNK_LID: "M58 302 Q110 309 162 302 L163 352 L57 352 Z",
  REAR_BUMPER: "M31 356 L189 356 C189 378 150 392 110 392 C70 392 31 378 31 356 Z",
  // Sol yan: camurluklar ve kapilar; tamponlar govde genisliginde, koseleri onlar yuvarlar
  LEFT_FRONT_FENDER: "M34 46 L53 46 L53 124 L27 124 Q27 80 34 46 Z",
  LEFT_FRONT_DOOR: "M27 128 L53 128 L53 213 L27 213 Q25 170 27 128 Z",
  LEFT_REAR_DOOR: "M27 217 L53 217 L53 298 L27 298 Q25 258 27 217 Z",
  LEFT_REAR_FENDER: "M27 302 L53 302 L53 352 L31 352 Q27 330 27 302 Z",
  // Sag yan: soldakinin aynasi
  RIGHT_FRONT_FENDER: "M186 46 L167 46 L167 124 L193 124 Q193 80 186 46 Z",
  RIGHT_FRONT_DOOR: "M193 128 L167 128 L167 213 L193 213 Q195 170 193 128 Z",
  RIGHT_REAR_DOOR: "M193 217 L167 217 L167 298 L193 298 Q195 258 193 217 Z",
  RIGHT_REAR_FENDER: "M193 302 L167 302 L167 352 L189 352 Q193 330 193 302 Z",
};

export const GLASS = [
  "M62 127 Q110 117 158 127 L151 170 Q110 164 69 170 Z", // on cam
  "M68 267 Q110 273 152 267 L158 297 Q110 304 62 297 Z", // arka cam
];

/** Tekerlekler (govdenin altinda, yanlardan tasar): x, y, genislik, yukseklik. */
export const WHEELS: [number, number, number, number][] = [
  [18, 66, 13, 46], [189, 66, 13, 46],     // on
  [17, 300, 13, 46], [190, 300, 13, 46],   // arka
];

/** Yan aynalar. */
export const MIRRORS = [
  "M27 134 L15 140 Q12 146 16 150 L27 148 Z",
  "M193 134 L205 140 Q208 146 204 150 L193 148 Z",
];
