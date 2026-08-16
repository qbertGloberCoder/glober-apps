package me.qbert.mapper.services;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import me.qbert.ui.ImageTransformerI;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.AbstractImageRenderer;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.mapper.ui.components.Canvas;
import me.qbert.mapper.ui.components.TextRenderer;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.service.ContourLineGenerator;
import me.qbert.mapper.model.AnimationConfiguration;
import me.qbert.mapper.model.ContourLine;
import me.qbert.mapper.model.ContourSweepLine;
import me.qbert.mapper.model.PinInformation;
import me.qbert.mapper.model.PinPing;
import me.qbert.mapper.model.SweepConfiguration;
import me.qbert.mapper.renderers.PinnableCelestialObject;
import me.qbert.ui.renderers.BoundaryContainerRenderer;
import me.qbert.ui.renderers.ColorRenderer;
import me.qbert.ui.renderers.ImageRenderer;
import me.qbert.ui.renderers.LineRenderer;
import me.qbert.ui.renderers.PolyRenderer;

public abstract class AbstractProjectionObject implements ImageTransformerI, ArcRendererLocationSetterI {
	private class TimeToStation {
		PinInformation station;
		double time;
	}
	private class TimeToStationComparator implements Comparator<TimeToStation> {
	    @Override
	    public int compare(TimeToStation t1, TimeToStation t2) {
	        return Double.compare(t1.time, t2.time);
	    }
	}	
	
	private static final double CIRCUMFERENCE = 40030.17359191636;
	private static final double MARGIN_FRACTION = 0.005;
	
	private ArrayList<TimeToStation> timeToStations = new ArrayList<TimeToStation>();
	private ArrayList<TimeToStation> stationsPinged = new ArrayList<TimeToStation>();
	
	private AbstractImageRenderer background;
	List<RendererI> backgroundRenderer = new ArrayList<RendererI>();
	private Canvas canvas;
	
	private AnimationConfiguration animationConfiguration;
	
	private TextRenderer elapsedTimeRenderer;
	
	private long eventTime;
	
	private Calendar eventDate;
	
	private HashMap<PinInformation, PinnableCelestialObject> pinToUILookup = new HashMap<PinInformation, PinnableCelestialObject>(); 
	private HashMap<ContourSweepLine, LineRenderer> sweepLineToUILookup = new HashMap<ContourSweepLine, LineRenderer>(); 

	private BufferedImage image = null;
	
	private int frame = 1;
	
	public AbstractProjectionObject(Canvas canvas, AnimationConfiguration animationConfiguration) throws Exception {
		eventTime = 0;

		eventDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		eventDate.setTimeInMillis(animationConfiguration.getEventTime().getTimeInMillis());
		
		this.canvas = canvas;
		this.animationConfiguration = animationConfiguration;

		ArrayList<SweepConfiguration> sweepConfigurations = animationConfiguration.getSweepConfigurations();
		for (SweepConfiguration sweepConfiguration : sweepConfigurations) {
			for (PinPing pinPing : sweepConfiguration.getPinPingTimes()) {
				TimeToStation tts = new TimeToStation();
				tts.time = pinPing.getTime();
				tts.station = pinPing.getPin();
				timeToStations.add(tts);
			}
		}
		Collections.sort(timeToStations, new TimeToStationComparator());
		
        init();
        
        BoundaryContainerRenderer backgroundBoundaryRenderer = new BoundaryContainerRenderer();
        backgroundBoundaryRenderer.setRenderComponent(true);
        List<RendererI> observerContourLinesRenderers = new ArrayList<RendererI>();
        
        ColorRenderer colorRenderer;
        
        // NOTE: The multiple loops through the sweep configurations are needed to stack things in their proper order
		for (SweepConfiguration sweepConfiguration : sweepConfigurations) {
			ContourLine contourLine = sweepConfiguration.getContourSweepLine().getContourLine();
			int widthTransform = (contourLine.isFixedWidthMode() ? LineRenderer.ABSOLUTE_COORDINATES : LineRenderer.FRACTIONAL_COORDINATES);
	        colorRenderer = new ColorRenderer();
	        colorRenderer.setBackgroundColor(contourLine.getColor());
	        colorRenderer.setForegroundColor(contourLine.getColor());
	        observerContourLinesRenderers.add(colorRenderer);
	        
	        LineRenderer observerContourLine = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
			observerContourLine.setLineConnectionPacmanMode(true);
			observerContourLine.setRenderComponent(true);
			observerContourLine.setLineWidth(contourLine.getThickness(), widthTransform);
			observerContourLinesRenderers.add(observerContourLine);
			
			sweepLineToUILookup.put(sweepConfiguration.getContourSweepLine(), observerContourLine);
		}
		
		BoundaryContainerRenderer observerContourLinesContainerRenderer = new BoundaryContainerRenderer();
		observerContourLinesContainerRenderer.setShiftDirectionX(0);
		observerContourLinesContainerRenderer.setBoundMinimumXFraction(0);
		observerContourLinesContainerRenderer.setBoundMaximumXFraction(1.0);
		observerContourLinesContainerRenderer.setBoundMinimumYFraction(0);
		observerContourLinesContainerRenderer.setBoundMaximumYFraction(1.0);
		observerContourLinesContainerRenderer.setMaintainAspectRatio(true);
		observerContourLinesContainerRenderer.setAspectRatioModifier(1.0);
		observerContourLinesContainerRenderer.setFollowContainer(backgroundBoundaryRenderer);
		observerContourLinesContainerRenderer.setRenderers(observerContourLinesRenderers);
        
        background = getBackgroundImageRenderer();
		background.setRenderComponent(true);
		background.setMaintainAspectRatio(true);

        backgroundRenderer.add(background);

        backgroundBoundaryRenderer.setRenderers(backgroundRenderer);
		
        List<RendererI> renderers = new ArrayList<RendererI>();

        List<RendererI> foregroundRenderer = new ArrayList<RendererI>();
        
		for (SweepConfiguration sweepConfiguration : sweepConfigurations) {
			for (PinInformation pin : sweepConfiguration.getPins()) {
				PinnableCelestialObject station = new PinnableCelestialObject(this);
	        	station.configure(pin.getColor(), Color.black, foregroundRenderer);
				station.updatePin(pin.getLatitude(), pin.getLongitude(),true);
				pinToUILookup.put(pin, station);
			}
		}
		
//        stations = new PinnableCelestialObject[coordinatesP.length + coordinatesPK.length];

        BoundaryContainerRenderer foregroundBoundaryRenderer = new BoundaryContainerRenderer();
        foregroundBoundaryRenderer.setShiftDirectionX(0);
        foregroundBoundaryRenderer.setBoundMinimumXFraction(MARGIN_FRACTION);
        foregroundBoundaryRenderer.setBoundMaximumXFraction(1.0 - MARGIN_FRACTION);
        foregroundBoundaryRenderer.setBoundMinimumYFraction(MARGIN_FRACTION);
        foregroundBoundaryRenderer.setBoundMaximumYFraction(1.0 - MARGIN_FRACTION);
        foregroundBoundaryRenderer.setMaintainAspectRatio(true);
        foregroundBoundaryRenderer.setAspectRatioModifier(1.0);
        foregroundBoundaryRenderer.setRenderers(foregroundRenderer);
        foregroundBoundaryRenderer.setFollowContainer(backgroundBoundaryRenderer);

        PolyRenderer floodFiller = new PolyRenderer(PolyRenderer.FRACTIONAL_COORDINATES);
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.white);
        colorRenderer.setForegroundColor(Color.white);
        renderers.add(colorRenderer);
        double fillX [] = {0.0, 1.0, 1.0, 0.0, 0.0};
        double fillY [] = {0.0, 0.0, 1.0, 1.0, 0.0};
        floodFiller.setX(fillX);
        floodFiller.setY(fillY);
        floodFiller.setFill(true);
        renderers.add(floodFiller);
        

        elapsedTimeRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        elapsedTimeRenderer.setMaintainAspectRatio(false);
        elapsedTimeRenderer.setX(10);
        elapsedTimeRenderer.setY(50);
        elapsedTimeRenderer.setText("---");
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.black);
        colorRenderer.setForegroundColor(Color.black);

        
        renderers.add(backgroundBoundaryRenderer);
        renderers.add(observerContourLinesContainerRenderer);
        renderers.add(foregroundBoundaryRenderer);
        renderers.add(colorRenderer);
        renderers.add(elapsedTimeRenderer);

        image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        canvas.setRecordMode(true);
        
		updateEruption();
		
        canvas.setRenderers(renderers);
	}
	
	public boolean tickAhead() {
		if (canvas.isCurrentlyRendering())
			return true;
		
		if ((timeToStations.size() < 1) && (stationsPinged.size() < 1))
			return false;
		
		eventTime += animationConfiguration.getEventSecondsPerFrame();
		updateEruption();
		
		canvas.paintToImage(image, true);
		
		String fname = String.format("output/test_%06d.jpg", frame ++);
		
		try {
			ImageIO.write(stripAlphaChannel(image), "jpg", new File(fname));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		canvas.repaint();
		
		return true;
	}
	
	private BufferedImage stripAlphaChannel(BufferedImage image) {
	    BufferedImage clone = new BufferedImage(image.getWidth(),
	            image.getHeight(), BufferedImage.TYPE_INT_RGB);
	    Graphics2D g2d = clone.createGraphics();
	    g2d.drawImage(image, 0, 0, null);
	    g2d.dispose();
	    return clone;
	}
	
	public void updateEruption() {
		for (SweepConfiguration sweepConfiguration : animationConfiguration.getSweepConfigurations()) {
			ContourSweepLine contourLine = sweepConfiguration.getContourSweepLine();
			LineRenderer contourLineRenderer = sweepLineToUILookup.get(contourLine);

			if (contourLineRenderer != null) {
				Double distance = contourLine.getPropagationDistanceForTime(eventTime, true);
				if (distance == null) {
					contourLineRenderer.setRenderComponent(false);
				} else {
					contourLineRenderer.setRenderComponent(true);
					while (distance > CIRCUMFERENCE)
						distance -= CIRCUMFERENCE;
					if (distance > (CIRCUMFERENCE/2.0))
						distance = CIRCUMFERENCE - distance;
					
					ObserverLocation contourLineCenter = new ObserverLocation();
					contourLineCenter.setGeoLocation(contourLine.getContourLine().getLatitude(), contourLine.getContourLine().getLongitude());
					updateContourLine(contourLineCenter, distance, contourLineRenderer);
				}
			}
		}
        
        updateStations();

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(eventDate.getTimeInMillis() + (eventTime*1000L));
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int minute = cal.get(Calendar.MINUTE);
		int second = cal.get(Calendar.SECOND);
		int milliSecond = cal.get(Calendar.MILLISECOND);
		double tdstOffset = (double)(cal.get(Calendar.DST_OFFSET) / 3600000.0);
		double tzOffset = (double)(cal.get(Calendar.ZONE_OFFSET) / 3600000.0);
		double timezoneAdjust = tzOffset + tdstOffset;
        
		String subSecond = "";
		subSecond = String.format(".%03d", milliSecond);
        elapsedTimeRenderer.setText(String.format("%04d-%02d-%02d %02d:%02d:%02d UTC", year, month, day, hour, minute, second));

	}
	
	private void updateStations() {
		/// This should be a reasonable rule of thumb but needs to be adjusted. It assumes a 30 fps render
		long timeToReset = 40*animationConfiguration.getEventSecondsPerFrame();
		while ((timeToStations.size() > 0) && ((long)timeToStations.getFirst().time < eventTime)) {
			TimeToStation tts = timeToStations.removeFirst();
			stationsPinged.add(tts);
		}

		long pingResetTime = eventTime - timeToReset;
		while ((stationsPinged.size() > 0) && ((long)stationsPinged.getFirst().time < pingResetTime)) {
			TimeToStation tts = stationsPinged.removeFirst();
			PinnableCelestialObject renderObject = pinToUILookup.get(tts.station);
			if (renderObject != null) {
				adjustPin(renderObject, tts.station.getInnerPinSize());
				renderObject.updatePin(true);
			}
		}
		for (int i = 0;i < stationsPinged.size();i ++ ) {
			TimeToStation tts = stationsPinged.get(i);
			int newPinSize = (18 * ((int)timeToReset - (int)(eventTime - tts.time))/(int)(timeToReset)) + tts.station.getInnerPinSize();
			PinnableCelestialObject renderObject = pinToUILookup.get(tts.station);
			if (renderObject != null) {
				adjustPin(renderObject, newPinSize);
				renderObject.updatePin(true);
			}
		}
	}
	
	protected abstract void adjustPin(PinnableCelestialObject object, int size);
	
	protected abstract void init();
	
	protected AbstractImageRenderer getBackgroundImageRenderer() {
		return new ImageRenderer(new File("projections/" + getProjection() + "/map.png"), null);
	}
	
	protected abstract String getProjection();
	
	private void updateContourLine(ObserverLocation location, double distance, LineRenderer contourLine) {
		ArrayList<java.lang.Double> fractionX1 = new ArrayList<java.lang.Double>();
		ArrayList<java.lang.Double> fractionY1 = new ArrayList<java.lang.Double>();
		ArrayList<java.lang.Double> fractionX2 = new ArrayList<java.lang.Double>();
		ArrayList<java.lang.Double> fractionY2 = new ArrayList<java.lang.Double>();
		
		ArrayList<Point2D.Double> points = ContourLineGenerator.computeContourLines(this, location, distance);
		
		Point2D.Double lastPoint = null;
		for (Point2D.Double point : points) {
			if (lastPoint != null) {
				fractionX1.add(lastPoint.x);
				fractionY1.add(lastPoint.y);
				fractionX2.add(point.x);
				fractionY2.add(point.y);
			}
			
			lastPoint = point;
		}
		
		contourLine.setX1Array(arrayListToArray(fractionX1));
		contourLine.setY1Array(arrayListToArray(fractionY1));
		contourLine.setX2Array(arrayListToArray(fractionX2));
		contourLine.setY2Array(arrayListToArray(fractionY2));
	}
	
	private double [] arrayListToArray(ArrayList<java.lang.Double> arrayList) {
		double [] d = new double[arrayList.size()];
		
		for (int i = 0;i < d.length;i ++) {
			d[i] = arrayList.get(i);
		}
		
		return d;
	}
	
	@Override
	public void updateLocation(ArcRenderer arc, double latitude, double longitude) {
		updateLocation(arc, latitude, longitude, 8);
	}
	
	@Override
	public void updateLocation(ArcRenderer arc, double latitude, double longitude, int size) {
		updateLocation(arc, latitude, longitude, size, false);
	}
	
	@Override
	public void updateLocation(ArcRenderer arc, double latitude, double longitude, int size, boolean renderFullCircumferenceSize) {
		Point2D.Double p2d = updateLocation(latitude, longitude, renderFullCircumferenceSize);

		if (p2d == null) {
			arc.setRenderComponent(false);
			return;
		}
		
		arc.setRenderComponent(true);
		
//		System.out.println("??? fraction " + fractionX + "," + fractionY + " FOR: " + latitude + "," + longitude + " USING: " + radius + "," + Math.toDegrees(angle));
		
		arc.setArcAngle(360);
		arc.setFill(true);
		arc.setWidth(size);
		arc.setHeight(size);
		arc.setX(p2d.x);
		arc.setY(p2d.y);
	}


	@Override
	public void transformImage(RendererI renderer, BufferedImage image) {
		if ((image == null) || (renderer == null))
			return;
	}
	
	protected ObserverLocation getObserverLocation() {
		return animationConfiguration.getObserver();
	}
}
