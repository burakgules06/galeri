package com.galeri.app.service.impl;

import com.galeri.app.exception.runtime.InvalidPhotoException;
import com.galeri.app.service.PhotoStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// Fotograflar <galeri.upload-dir>/<tenantId>/<photoId>.jpg ve <photoId>_t.jpg olarak durur.
// Her yuklemede yeniden kodlanir: boyut sabitlenir, EXIF (konum dahil) atilir, resim olmayan dosya reddedilir.
@Slf4j
@Service
public class DiskPhotoStorage implements PhotoStorage {

    static final int MAX_SIDE = 1600;
    static final int THUMB_SIDE = 480;
    static final int LOGO_SIDE = 600;
    private static final long MAX_PIXELS = 50_000_000L;   // bozuk / kotu niyetli dev resimlere karsi

    private final Path root;

    public DiskPhotoStorage(@Value("${galeri.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public Stored save(UUID tenantId, UUID photoId, byte[] content) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException | RuntimeException e) {
            throw new InvalidPhotoException();
        }
        if (source == null || (long) source.getWidth() * source.getHeight() > MAX_PIXELS) throw new InvalidPhotoException();

        BufferedImage large = scale(source, MAX_SIDE);
        byte[] largeJpeg = jpeg(large, 0.82f);
        byte[] thumbJpeg = jpeg(scale(source, THUMB_SIDE), 0.78f);
        try {
            Path dir = Files.createDirectories(root.resolve(tenantId.toString()));
            Files.write(dir.resolve(photoId + ".jpg"), largeJpeg);
            Files.write(dir.resolve(photoId + "_t.jpg"), thumbJpeg);
        } catch (IOException e) {
            throw new UncheckedIOException("Fotograf diske yazilamadi", e);
        }
        log.info("Fotograf kaydedildi: {} ({}x{}, {} bayt)", photoId, large.getWidth(), large.getHeight(), largeJpeg.length);
        return new Stored(large.getWidth(), large.getHeight(), largeJpeg.length);
    }

    @Override
    public Stored saveLogo(UUID tenantId, UUID logoId, byte[] content) {
        BufferedImage logo = scaleKeepAlpha(read(content), LOGO_SIDE);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(logo, "png", png);
            Path dir = Files.createDirectories(root.resolve(tenantId.toString()));
            Files.write(dir.resolve(logoFile(logoId)), png.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Logo diske yazilamadi", e);
        }
        log.info("Logo kaydedildi: {} ({}x{}, {} bayt)", logoId, logo.getWidth(), logo.getHeight(), png.size());
        return new Stored(logo.getWidth(), logo.getHeight(), png.size());
    }

    @Override
    public void deleteLogo(UUID tenantId, UUID logoId) {
        try {
            Files.deleteIfExists(root.resolve(tenantId.toString()).resolve(logoFile(logoId)));
        } catch (IOException e) {
            log.warn("Logo dosyasi silinemedi: {} | {}", logoId, e.getMessage());
        }
    }

    @Override
    public Path logoPath(UUID tenantId, UUID logoId) {
        Path p = root.resolve(tenantId.toString()).resolve(logoFile(logoId));
        return Files.isRegularFile(p) ? p : null;
    }

    private static String logoFile(UUID logoId) {
        return "logo-" + logoId + ".png";
    }

    private static BufferedImage read(byte[] content) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException | RuntimeException e) {
            throw new InvalidPhotoException();
        }
        if (source == null || (long) source.getWidth() * source.getHeight() > MAX_PIXELS) throw new InvalidPhotoException();
        return source;
    }

    /** Uzun kenar en fazla max (buyutmez); saydam alanlar korunur (logo). */
    static BufferedImage scaleKeepAlpha(BufferedImage src, int max) {
        double ratio = Math.min(1.0, (double) max / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    @Override
    public void delete(UUID tenantId, UUID photoId) {
        Path dir = root.resolve(tenantId.toString());
        try {
            Files.deleteIfExists(dir.resolve(photoId + ".jpg"));
            Files.deleteIfExists(dir.resolve(photoId + "_t.jpg"));
        } catch (IOException e) {
            log.warn("Fotograf dosyasi silinemedi: {} | {}", photoId, e.getMessage());
        }
    }

    @Override
    public Path path(UUID tenantId, UUID photoId, boolean thumb) {
        Path p = root.resolve(tenantId.toString()).resolve(photoId + (thumb ? "_t.jpg" : ".jpg"));
        return Files.isRegularFile(p) ? p : null;
    }

    /** Uzun kenar en fazla max olacak sekilde kucultur (buyutmez); saydam alanlar beyaz olur. */
    static BufferedImage scale(BufferedImage src, int max) {
        double ratio = Math.min(1.0, (double) max / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] jpeg(BufferedImage img, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
