/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.catalogue.sync;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Shrinks catalogue photos for the web: max 1200 px on the long side, JPEG quality 0.85,
 * transparent areas on white. GIF (may be animated) and WebP are passed through unchanged.
 */
final class ImageOptimizer {

    static final int MAX_SIDE = 1200;

    private ImageOptimizer() {}

    record Result(byte[] bytes, String contentType, String extension) {}

    static Result optimise(CatalogueSource.Photo photo) {
        String type = photo.contentType();
        if (!type.equals("image/png") && !type.equals("image/jpeg")) return null;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(photo.content()));
            if (src == null) return null;
            double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(src.getWidth(), src.getHeight()));
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            // Keep the original if re-encoding didn't make it smaller.
            if (bytes.size() >= photo.content().length && scale == 1.0) return null;
            return new Result(bytes.toByteArray(), "image/jpeg", ".jpg");
        } catch (Exception | OutOfMemoryError e) {
            return null; // fall back to the original file
        }
    }
}
