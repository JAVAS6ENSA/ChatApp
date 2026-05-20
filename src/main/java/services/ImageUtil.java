package services;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;


public final class ImageUtil {

    private ImageUtil() {}


    public static byte[] compressAvatar(File source) throws Exception {
        BufferedImage src = ImageIO.read(source);
        if (src == null) return null;
        int target = 256;
        BufferedImage scaled = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, target, target, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(scaled, "jpg", out);
        return out.toByteArray();
    }
}
