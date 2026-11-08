package com.galeri.app.model.response;

import com.galeri.app.model.enums.BodyPart;
import com.galeri.app.model.enums.Fuel;
import com.galeri.app.model.enums.PartCondition;
import com.galeri.app.model.enums.Transmission;

import java.util.List;
import java.util.UUID;

/**
 * Vitrin cevaplari. Herkese acik (Public*) cevaplarda alis fiyati, maliyet, satici, sahiplik, ic notlar ASLA yoktur;
 * fiyat sadece galericinin girdigi ilan fiyatidir.
 */
public final class ShowroomResponses {

    private ShowroomResponses() {}

    /** url / thumbUrl API kokune gore goreli: /api/public/photos/{id}.jpg */
    public record Photo(UUID id, String url, String thumbUrl, int width, int height) {}

    /** Panel: aracin vitrin ayarlari ve fotograflari (yonetici). */
    public record Listing(boolean showInShowroom, Long listingPriceCents, String listingDescription, List<Photo> photos,
                          boolean inStock) {}

    /** Panel: vitrin linki ve WhatsApp numarasi. */
    public record Settings(String slug, String phone) {}

    public record PublicGallery(String name, String phone, List<PublicCard> vehicles) {}

    public record PublicCard(UUID id, String make, String model, short modelYear, int mileageKm, Fuel fuel,
                             Transmission transmission, String color, Long priceCents, Photo cover, int photoCount,
                             int paintedParts, int replacedParts) {}

    public record PublicPart(BodyPart part, PartCondition condition) {}

    public record PublicVehicle(UUID id, String plate, String make, String model, short modelYear, int mileageKm, Fuel fuel,
                                Transmission transmission, String color, Long priceCents, Long tramerCents, String description,
                                List<PublicPart> parts, List<Photo> photos, String galleryName, String phone) {}
}
