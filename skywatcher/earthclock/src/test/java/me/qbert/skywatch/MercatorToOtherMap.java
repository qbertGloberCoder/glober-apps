package me.qbert.skywatch;

import java.awt.geom.Point2D.Double;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import me.qbert.skywatch.service.projections.MercatorTransform;
import me.qbert.skywatch.ui.renderers.GlobeImageRenderer;

public class MercatorToOtherMap {
	private enum OutputType {
//		Mercator,
		EQUIRECTANGULAR,
		AENORTH,
		SPHERE
	}
	
	private static final double latBoundary = 85.12;
	private static MercatorTransform transformer = new MercatorTransform();
	
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
    
    private static int mergePixels(int pixel1, int pixel2, double percentPixel2) {
    	if (percentPixel2 <= 0.0)
    		return pixel1;
    	if (percentPixel2 >= 1.0)
    		return pixel2;
    	
    	double percentPixel1 = 1.0 - percentPixel2;
    	
 //   	int a = ((((pixel1 >> 24) & 0xFF) + ((pixel2 >> 24) & 0xFF))/2) & 0xFF;
    	int r = (int)(((percentPixel1*(double)((pixel1 >> 16) & 0xFF)) + (percentPixel2*(double)((pixel2 >> 16) & 0xFF)))) & 0xFF;
    	int g = (int)(((percentPixel1*(double)((pixel1 >> 8) & 0xFF)) + (percentPixel2*(double)((pixel2 >> 8) & 0xFF)))) & 0xFF;
    	int b = (int)(((percentPixel1*(double)(pixel1 & 0xFF)) + (percentPixel2*(double)(pixel2 & 0xFF)))) & 0xFF;
    	return 0xFF000000 | (r << 16) | (g << 8) | (b);
    }
    
    private static int getRGBValue(int defaultRGB, int iWidth, int iHeight, int iMidX, int iMidY, double latHeight, double lonWidth, BufferedImage image, double latitude, double longitude) {
		int rgb = 0xFF7F7F7F;
		if ((latitude < latBoundary) && (latitude > -latBoundary)) {
			Double point = transformer.transform(latitude, longitude, 0.0, 0.0, 0.0);
			double px = (point.x - 0.5);
			double py = (point.y - 0.5);
	    	
			double ixd = ((double)iMidX + ((double)lonWidth * px));
			double iyd = ((double)iMidY + ((double)latHeight * py));
			int ix = (int)ixd;
			int iy = (int)iyd;
			
			if (ix < 0)
				ix += lonWidth;
			else if (ix >= iWidth)
				ix -= lonWidth;
			
			if ((ix < 0) || (ix >= iWidth) || (iy < 0) || (iy >= iHeight))
				rgb = defaultRGB;
			else if ((ix == 0) || (ix == iWidth-1) || (iy == 0) || (iy == iHeight-1))
				rgb = image.getRGB(ix, iy);
			else {
				int rgb3 = image.getRGB(ix, iy + 1);
				int rgb4 = image.getRGB(ix + 1, iy + 1);
				
				double xDiff = (ixd - (double)ix);
				double yDiff = (iyd - (double)iy);
				
				int rgbT = mergePixels(image.getRGB(ix, iy), image.getRGB(ix + 1, iy), xDiff);
				int rgbB = mergePixels(image.getRGB(ix, iy), image.getRGB(ix + 1, iy), xDiff);
				rgb = mergePixels(rgbT, rgbB, yDiff);
			}
		}
		
		return rgb;
    }
    
    protected static void transformToAENorthMap(BufferedImage defaultProjectionMap, int iMidX, int iMidY, double latHeight, double lonWidth, BufferedImage image, BufferedImage out) {
    	int iWidth = image.getWidth();
    	int iHeight = image.getHeight();
    	
    	int oWidth = out.getWidth();
		int oHeight = out.getHeight();
		int oMidX = oWidth / 2;
		int oMidY = oHeight / 2;

		double r = oMidY;
		
		int rgb = 0;

		double rotateAngleRads = (Math.toRadians(90));
		
		for (int y = -oMidY;y < oMidY;y ++) {
			int maxX = (int)(Math.sqrt((r*r)-(y*y)));
			for (int x = -maxX;x < maxX;x ++) {
				double latitude = 90 - Math.abs(180.0 * (double)((Math.sqrt(x*x+y*y)))/(double)(oMidY));
				double longitude = Math.toDegrees(Math.atan2(-y, x));
				
				double sampleA = Math.atan2(y, x);
				double sampleR = Math.sqrt(x*x+y*y);
				sampleA += rotateAngleRads;
				
				int sampleX = (int)(Math.cos(sampleA) * sampleR);
				int sampleY = (int)(Math.sin(sampleA) * sampleR);
				
				if ((latitude < latBoundary) && (latitude > -latBoundary)) {
					rgb = getRGBValue(defaultProjectionMap.getRGB(oMidX + sampleX, oMidY + sampleY), iWidth, iHeight, iMidX, iMidY, latHeight, lonWidth, image, latitude, longitude);
				} else
					rgb = defaultProjectionMap.getRGB(oMidX + sampleX, oMidY + sampleY);
				out.setRGB(oMidX + x, oMidY + y, rgb);
			}
		}
    }

    protected static void transformToEquirectangular(BufferedImage defaultProjectionMap, int iMidX, int iMidY, double latHeight, double lonWidth, BufferedImage image, int bannerTop, BufferedImage out) {
    	int iWidth = image.getWidth();
    	int iHeight = image.getHeight();
    	
    	int oWidth = out.getWidth();
		int oHeight = out.getHeight() - bannerTop;
		int oMidX = oWidth / 2;
		int oMidY = oHeight / 2;

		int rgb = 0;

		for (int y = 0;y < oHeight;y ++) {
			double latitude = (90.0 * (double)(oMidY - y)/(double)(oMidY));
			for (int x = 0;x < oWidth;x ++) {
				if ((latitude < latBoundary) && (latitude > -latBoundary)) {
					double longitude = (180.0 * (double)(x - oMidX)/(double)(oMidX));
					rgb = getRGBValue(defaultProjectionMap.getRGB(x, y + bannerTop), iWidth, iHeight, iMidX, iMidY, latHeight, lonWidth, image, latitude, longitude);
				} else
					rgb = defaultProjectionMap.getRGB(x, y + bannerTop);
				out.setRGB(x, y + bannerTop, rgb);
			}
		}
    }
    
    private static void convertOneFile(BufferedImage defaultProjectionMap, String prefix, String file, String extraOutput, String suffix, 
    		int iMidX, int iMidY, int latHeight, int lonWidth,
    		int bannerTop, File intermediate, OutputType outputType, double latitude, double longitude) throws NullPointerException, IOException {
		System.out.println("Processing: " + file + suffix);
		BufferedImage image = loadImageFromFile(new File(prefix + file + suffix));

		
		int oWidth = defaultProjectionMap.getWidth();
		int oHeight = defaultProjectionMap.getHeight();
		BufferedImage out;
		
		if ((outputType == OutputType.EQUIRECTANGULAR) || (outputType == OutputType.SPHERE)) {
			out = new BufferedImage(oWidth, oHeight, BufferedImage.TYPE_INT_RGB);
			transformToEquirectangular(defaultProjectionMap, iMidX, iMidY, latHeight, lonWidth, image, bannerTop, out);
			
			BufferedImage buff = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_RGB);
			buff.createGraphics().drawImage(out, 0, 0 ,null);
			GlobeImageRenderer globalRenderer = new GlobeImageRenderer(buff);

			if (outputType == OutputType.SPHERE) {
//			double oLat = 75; //-60;
//			double oLon = 0; //-150;
			buff = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_RGB);
			globalRenderer.setRenderDimensions(0, 0, out.getWidth(), out.getHeight());
	        globalRenderer.wrapToCoordinates(latitude, longitude);

	        BufferedImage globeImage = globalRenderer.getLastBi();
	        
			buff.createGraphics().drawImage(globeImage, (buff.getWidth() - globeImage.getWidth())/2, (buff.getHeight() - globeImage.getHeight())/2 ,null);
			}
			
//			File outFile = new File("dm_output/" + file + String.format("_%02d_%03d", oLat, oLon) + ".jpg");
			File outFile = new File("dm_output/" + file + extraOutput + ".jpg");

			ImageIO.write(buff, "jpg", outFile);
		} else if (outputType == OutputType.AENORTH) {
			out = new BufferedImage(oWidth, oHeight, BufferedImage.TYPE_INT_RGB);
			transformToAENorthMap(defaultProjectionMap, iMidX, iMidY, latHeight, lonWidth, image, out);
			
			File outFile = new File("dm_output/" + file + extraOutput + ".jpg");
			ImageIO.write(out, "jpg", outFile);
		} else
			return;
		
//		for (int oLat = 45;oLat >= -45;oLat -= 45) {
//			for (int oLon = -180;oLon < 180;oLon += 45) {
				BufferedImage buff = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_RGB);
				buff.createGraphics().drawImage(out, 0, 0 ,null);
		
				ImageIO.write(buff, "jpg", intermediate);

/*					GlobeImageRenderer globalRenderer = new GlobeImageRenderer(buff);
				
				buff = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_RGB);
				globalRenderer.setRenderDimensions(0, 0, out.getWidth(), out.getHeight());
		        globalRenderer.wrapToCoordinates(oLat, oLon);
		
		        BufferedImage globeImage = globalRenderer.getLastBi();
		        
				buff.createGraphics().drawImage(globeImage, (buff.getWidth() - globeImage.getWidth())/2, (buff.getHeight() - globeImage.getHeight())/2 ,null);
		
				outFile = new File("dm_output/" + file + String.format("_%02d_%03d", oLat, oLon) + ".jpg");
				ImageIO.write(buff, "jpg", outFile);
			}
		}*/
    }
    
    private static void convertOne(BufferedImage defaultProjectionMap, String prefix, String file, String extraOutput, String suffix, int iMidX, int iMidY, int latHeight, int lonWidth, int bannerTop, File intermediate, OutputType outputType, double latitude, double longitude) throws NullPointerException, IOException {
		convertOneFile(defaultProjectionMap, prefix, file, extraOutput, suffix,
				iMidX, iMidY, latHeight, lonWidth,
				bannerTop, intermediate, outputType, latitude, longitude);
/*
		int uzb102X1 = 738;
		int uzb102Y1 = 582;
		int uzb102X2 = 105;
		int uzb102Y2 = 438;

  		convertOneFile(defaultProjectionMap, "flights/uzb102/", "Screen Shot 2024-05-17 at 10.33.32 AM", ".png", 
				australiaX, australiaY, floridaX, floridaY, 
				uzb102X1, uzb102Y1, uzb102X2, uzb102Y2,
				bannerTop, intermediate, equirectangular); */
    }

    private static void convertOne(BufferedImage defaultProjectionMap, int iMidX, int iMidY, int latHeight, int lonWidth, int bannerTop, File intermediate, OutputType outputType, double latitude, double longitude) throws NullPointerException, IOException {
//    	convertOne(defaultProjectionMap, "flights/lan805/", "Screen Shot 2024-05-16 at 1.48.12 PM", ".png",  bannerTop, intermediate, equirectangular);
    	convertOne(defaultProjectionMap, "/Volumes/bcommon/bertstuff/output_trimmed/", "img_000001", "", ".png", 
    			iMidX, iMidY, latHeight, lonWidth, bannerTop, intermediate, outputType, latitude, longitude);
    }
    
    private static void batchConvert(BufferedImage defaultProjectionMap, int iMidX, int iMidY, int latHeight, int lonWidth, int bannerTop, File intermediate, OutputType outputType) throws NullPointerException, IOException {
    	double oLat = -30;	// -61
        double oLon = 0;	// -138
        
    	int firstId = 425;
    	int lastId = 2550;
    	
    	boolean align = false;
    	
    	int extraCount = 1;
    	
    	boolean renderLeader = true;
    	boolean renderMain = true;
    	boolean renderTrailer = true;

    	if (align) {
    		firstId = lastId = 2500; //(firstId + lastId) * 3 / 4;
    		renderLeader = false;
    		renderTrailer = false;
    	}
    	
    	if ((renderLeader) && (outputType == OutputType.SPHERE)) {
	    	for (int i = 90;i > -90;i --) {
				String file = String.format("img_%06d", firstId);
				String extra = String.format("-%06d", extraCount ++);
		    	System.out.print((90 - i) + " of " + 180 + ": ");
				convertOne(defaultProjectionMap, "/Volumes/bcommon/bertstuff/output_trimmed/", file, extra, ".png", 
						iMidX, iMidY, latHeight, lonWidth, bannerTop, intermediate, outputType, (double)i, oLon);
			}
	    	for (int i = -90;i < oLat;i ++) {
				String file = String.format("img_%06d", firstId);
				String extra = String.format("-%06d", extraCount ++);
		    	System.out.print((90 - i) + " of " + (90 - oLat) + ": ");
				convertOne(defaultProjectionMap, "/Volumes/bcommon/bertstuff/output_trimmed/", file, extra, ".png", 
						iMidX, iMidY, latHeight, lonWidth, bannerTop, intermediate, outputType, (double)i, oLon);
			}
    	}

    	if (renderMain) {
	    	for (int i = firstId;i <= lastId;i ++) {
				String file = String.format("img_%06d", i);
		    	System.out.print(i + " of " + lastId + ": ");
				convertOne(defaultProjectionMap, "/Volumes/bcommon/bertstuff/output_trimmed/", file, "", ".png", 
						iMidX, iMidY, latHeight, lonWidth, bannerTop, intermediate, outputType, oLat, oLon);
			}
    	}

    	if ((renderTrailer) && (outputType == OutputType.SPHERE)) {
	    	extraCount = 1;
	    	double workinglat = oLat;
	    	
	    	for (int i = 0;i <= 360;i += 2) {
	    		double lon = (oLon + i);
	    		while (lon > 180.0)
	    			lon -= 360.0;
	    		
				String file = String.format("img_%06d", lastId);
				String extra = String.format("_%06d", extraCount ++);
		    	System.out.print(i + " of " + 360 + ": ");
				convertOne(defaultProjectionMap, "/Volumes/bcommon/bertstuff/output_trimmed/", file, extra, ".png", 
						iMidX, iMidY, latHeight, lonWidth, bannerTop, intermediate, outputType, (double)workinglat, lon);
				
				if (workinglat < -1.0)
					workinglat += 1.0;
				if (workinglat > 1.0)
					workinglat -= 1.0;
			}
    	}
    }
    
    public static void main(String[] args) throws NullPointerException, IOException {
		int chileX = 647;
		int chileY = 1413;
		int australiaX = 1672;
		int australiaY = 1181;
		int floridaX = 404;
		int floridaY = 894;
		int greenlandX = 780;
		int greenlandY = 603;
		
		/// UPDATE THESE
		int qfa27X1 = 171; //169;
		int qfa27Y1 = 337; //260;
		int qfa27X2 = 563; //561;
		int qfa27Y2 = 194; //117;
		
		int qfa28X1 = 192; //169;
		int qfa28Y1 = 371; //260;
		int qfa28X2 = 772; //561;
		int qfa28Y2 = 82; //117;
		
		int qfa63X1 = 532; //169;
		int qfa63Y1 = 361; //260;
		int qfa63X2 = 46; //561;
		int qfa63Y2 = 46; //117;
		
		int qfa64X1 = 687; //169;
		int qfa64Y1 = 430; //260;
		int qfa64X2 = 205; //561;
		int qfa64Y2 = 113; //117;
		
		int qfa65X1 = 733; //169;
		int qfa65Y1 = 427; //260;
		int qfa65X2 = 289; //561;
		int qfa65Y2 = 137; //117;
		
		int qfa66X1 = 684; //169;
		int qfa66Y1 = 457; //260;
		int qfa66X2 = 240; //561;
		int qfa66Y2 = 168; //117;
		
		int lan800X1 = 147; //169;
		int lan800Y1 = 400; //260;
		int lan800X2 = 827; //561;
		int lan800Y2 = 62; //117;
		
		int lan805X1 = 157; //169;
		int lan805Y1 = 295; //260;
		int lan805X2 = 549; //561;
		int lan805Y2 = 150; //117;
		
		int uae262X1 = 710; //169;
		int uae262Y1 = 429; //260;
		int uae262X2 = 266; //561;
		int uae262Y2 = 139; //117;
		
		int tam8059X1 = 668; //169;
		int tam8059Y1 = 455; //260;
		int tam8059X2 = 218; //561;
		int tam8059Y2 = 162; //117;
		
		int pointAX = australiaX;
		int pointAY = australiaY;
		int pointBX = greenlandX;
		int pointBY = greenlandY; 
		int fileAXp = tam8059X1;
		int fileAY = tam8059Y1;
		int fileBXp = tam8059X2;
		int fileBY = tam8059Y2;

		int iMidX = 752;
		int iMidY = 457;
		int latHeight = 820;
		int lonWidth = 820;
		
		int fileAX;
		int fileBX;
		
		boolean swapLon;
		if (fileAXp > fileBXp) {
			fileAX = fileAXp;
			fileBX = fileBXp;
			swapLon = false;
		} else {
			fileAX = fileAXp;
			fileBX = fileBXp;
			swapLon = true;
		}
		
		int targetX = 2058;
		int targetY = 2058;
		
		boolean isExit = false;
		
		double representativeWidth;
		int fileWidth;
		
		if (fileAXp > fileBXp) {
			System.out.println("Option A");
			representativeWidth = (double)targetX/((double)pointAX - (double)pointBX);
			fileWidth = fileAX - fileBX;
		}
		else {
			System.out.println("Option B");
			representativeWidth = (double)targetX/((double)targetX - Math.abs((double)pointBX - (double)pointAX));
			fileWidth = Math.abs(fileBX-fileAX);
		}
		
		System.out.println("??? X Multiplier? " + representativeWidth);
		System.out.println("??? file width? " + fileWidth);
		
		lonWidth = (int)((double)representativeWidth*fileWidth);
		System.out.println("??? target is " + (targetX/(targetX - Math.abs(pointAX - pointBX))));
		System.out.println("??? delta1 is " + Math.abs(pointAX - pointBX));
		System.out.println("??? delta2 is " + Math.abs(fileAX-fileBX));
		
		if (swapLon)
			fileAX += lonWidth;
		
		System.out.println("??? lonWidth is " + lonWidth);
		
		double scaleX = 180.0 * ((double)pointAX - ((double)targetX / 2.0)) / (double)(targetX/2.0);
		System.out.println("??? scaleX is " + scaleX);
		double scaleX2 = 180.0 * ((double)pointBX - ((double)targetX / 2.0)) / (double)(targetX/2.0);
		System.out.println("??? scaleX2 is " + scaleX2);
		
		double longitudeDelta = scaleX - scaleX2;
		System.out.println("??? longitudeDelta is " + longitudeDelta);
		while (longitudeDelta > 360.0)
			longitudeDelta -= 360.0;
		System.out.println("??? longitudeDelta is " + longitudeDelta);
		
		double primeMeridian = fileAX - ((fileAX - fileBX) * (scaleX / longitudeDelta));
		System.out.println("??? prime meridian is " + primeMeridian);
		while (primeMeridian > lonWidth)
			primeMeridian -= lonWidth;
		System.out.println("??? prime meridian is " + primeMeridian);
		if (primeMeridian < 0.0)
			primeMeridian += lonWidth;
		System.out.println("??? prime meridian is " + primeMeridian);
		
		double srcTargetMultiplier = ((double)fileAX-(double)fileBX)/((double)pointAX-(double)pointBX);
		int srcTargetX = (int)((double)targetX * srcTargetMultiplier);
		System.out.println("??? srcTargetMultiplier is " + srcTargetMultiplier);
		System.out.println("??? srcTargetX is " + srcTargetX);
		
		iMidX = (int)primeMeridian;
		
		double equator = fileAY - (double)(pointAY - targetY/2.0) / (double)(pointAY - pointBY) * (double)(fileAY - fileBY);
		System.out.println("??? equator is " + equator);
		iMidY = (int)equator;
//		latHeight = (int)((double)targetY * (double)(fileAY - fileBY) / (double)(pointAY - pointBY));
		latHeight = Math.abs((int)((double)(fileAY - fileBY) * (double)targetY / (double)(pointAY - pointBY)));

		System.out.println("??? iMidX is " + iMidX);
		System.out.println("??? iMidY is " + iMidY);
		System.out.println("??? latHeight is " + latHeight);
		System.out.println("??? lonWidth is " + lonWidth);
		
		if (isExit)
			return;
		
    	int bannerTop = 128;
    	OutputType outputType = OutputType.AENORTH;

		File intermediate;
		
		BufferedImage defaultProjectionMap;
		
		
		if (outputType == OutputType.EQUIRECTANGULAR) {
			intermediate = new File("test_equirectangular.jpg");
			defaultProjectionMap = loadImageFromFile(new File("projections/equirectilinear/map.png"));
		}
		else if (outputType == OutputType.SPHERE) {
			intermediate = new File("test_equirectangular.jpg");
			defaultProjectionMap = loadImageFromFile(new File("projections/equirectilinear/map.png"));
		}
		else if (outputType == OutputType.AENORTH) {
			intermediate = new File("test_aenorth.jpg");
			defaultProjectionMap = loadImageFromFile(new File("projections/ae-north/map.png"));
		} else {
			return;
		}
		
			
		batchConvert(defaultProjectionMap, iMidX, iMidY, latHeight, lonWidth, bannerTop, intermediate, outputType);
//		convertOne(defaultProjectionMap, bannerTop, intermediate, equirectangular);
	}

}
