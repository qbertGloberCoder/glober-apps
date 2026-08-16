package me.qbert.skywatch;

import java.awt.geom.Point2D.Double;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import me.qbert.skywatch.service.projections.MercatorTransform;
import me.qbert.skywatch.ui.renderers.GlobeImageRenderer;

public class MercatorToOtherMap2 {
    protected static BufferedImage loadImageFromFile(File file) throws NullPointerException,IOException {
    	BufferedImage newFile = null;
    	
    	newFile = ImageIO.read(file);
    	try {
    		while (newFile.getWidth(null) == -1)
    			Thread.sleep(500);
    	} catch (InterruptedException e) {
    		newFile = null;
    	} catch (NullPointerException e) {
    		// Let's print the offending file name and pretend no file was loaded
    		System.out.println("Unable to load file: " + file.getName());
    		newFile = null;
    		throw e;
    	}
    	
    	return newFile;
    }

	public static void main(String[] args) throws NullPointerException, IOException {
		// src stats:
		// equator x = 640, y = 404			==> 1022, 639   (-128)
		// prime meridian x = 		==> 1022
		// mercator sample (1028,1028)
		// james bay west: x = 306, y = 253   --> 556, 325		34.62890625N, 97.92563600782778864940w		55.06221159168403, -82.26549515211573
		//						
		//
		// 1096 146			--> 1661, 217
		
		
		for (int i = 117;i <= 295;i ++) {
			String file = String.format("img_%06d", i);
			BufferedImage image = loadImageFromFile(new File("dm_sample_mercator/" + file + ".png"));
	
			// equator y = 404, prime meridian = 640, w = 1280, h = 720
			int iWidth = 1280;
			int iHeight = 720;
			int iMidX = 655;
			int iMidY = 404;
			
			int oWidth = 2044;
			int oHeight = 1022;
			int oMidX = oWidth / 2;
			int oMidY = oHeight / 2;
			int bannerTop = 128;
			BufferedImage out = new BufferedImage(oWidth, oHeight + bannerTop, BufferedImage.TYPE_INT_RGB);
			int rgb = 0;
			
			double latBoundary = 85.12;
			
			double longitudeMultiplier = 0.89;
			
			MercatorTransform transformer = new MercatorTransform();
			for (int y = 0;y < oHeight;y ++) {
				double latitude = (90.0 * (double)(oMidY - y)/(double)(oMidY));
				for (int x = 0;x < oWidth;x ++) {
					if ((latitude < latBoundary) && (latitude > -latBoundary)) {
						double longitude = (180.0 * (double)(x - oMidX)/(double)(oMidX));
						Double point = transformer.transform(latitude, longitude, 0.0, 0.0, 0.0);
						double px = (point.x - 0.5) / longitudeMultiplier;
						double py = (point.y - 0.5);
						
						int ix = (int)(iMidX + ((iMidX*2) * px));
						int iy = (int)(iMidY + ((iMidY*2) * py));
						
						if ((ix < 0) || (ix >= iWidth) || (iy < 0) || (iy >= iHeight))
							rgb = 0xFF7F7F7F;
						else
							rgb = image.getRGB(ix, iy);
					} else
						rgb = 0xFF7F7F7F;
					out.setRGB(x, y + bannerTop, rgb);
				}
			}
			
			
			for (int oLat = 45;oLat >= -45;oLat -= 45) {
				for (int oLon = -180;oLon < 180;oLon += 45) {
					BufferedImage buff = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_RGB);
					buff.createGraphics().drawImage(out, 0, 0 ,null);
			
					GlobeImageRenderer globalRenderer = new GlobeImageRenderer(buff);
					
					buff = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_RGB);
					globalRenderer.setRenderDimensions(0, 0, out.getWidth(), out.getHeight());
			        globalRenderer.wrapToCoordinates(oLat, oLon);
			
			        BufferedImage globeImage = globalRenderer.getLastBi();
			        
					buff.createGraphics().drawImage(globeImage, (buff.getWidth() - globeImage.getWidth())/2, (buff.getHeight() - globeImage.getHeight())/2 ,null);
			
					File outFile = new File("dm_output/" + file + String.format("_%02d_%03d", oLat, oLon) + ".jpg");
					ImageIO.write(buff, "jpg", outFile);
				}
			}
		}
	}

}
