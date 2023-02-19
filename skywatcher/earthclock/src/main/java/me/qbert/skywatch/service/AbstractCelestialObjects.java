package me.qbert.skywatch.service;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.AbstractCelestialObject;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.astro.service.AbstractPrecession.PrecessionData;
import me.qbert.skywatch.astro.service.ContourLineGenerator;
import me.qbert.skywatch.astro.service.MoonPrecession;
import me.qbert.skywatch.astro.service.ProjectionTransformerI;
import me.qbert.skywatch.astro.service.SunPrecession;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.service.SwitchableProjectionObjects.ProjectionType;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.ui.renderers.PinnableCelestialObject;
import me.qbert.ui.ImageTransformerI;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.AbstractFractionRenderer;
import me.qbert.ui.renderers.AbstractImageRenderer;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.BoundaryContainerRenderer;
import me.qbert.ui.renderers.ColorRenderer;
import me.qbert.ui.renderers.ImageRenderer;
import me.qbert.ui.renderers.LineRenderer;
import me.qbert.ui.renderers.PolyRenderer;
import me.qbert.ui.renderers.TextRenderer;
import me.qbert.ui.renderers.VirtualImageCanvasRenderer;

/*
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

public abstract class AbstractCelestialObjects implements ImageTransformerI, ArcRendererLocationSetterI {
	public enum MapCenterMode {
		OBSERVER_LAT_LON("Observer lat/lon"),
		OBSERVER_LON("Observer longitude"),
		SUN("Sun"),
		MOON("Moon");
		
	    private String name;

	    private MapCenterMode(String stringVal) {
	        name=stringVal;
	    }
	    public String toString(){
	        return name;
	    }

	    public static String getEnumByString(String code){
	        for(MapCenterMode e : MapCenterMode.values()){
	            if(e.name.equals(code)) return e.name();
	        }
	        return null;
	    }
	}

	public static final MapCenterMode [] MAP_CENTER_MODES = {MapCenterMode.OBSERVER_LAT_LON, MapCenterMode.OBSERVER_LON, MapCenterMode.SUN, MapCenterMode.MOON};
	
	private static final int [] CONTOUR_ZENITH_ANGLES = {15,30,45,60,75,90,96,102,105,108,120,135,150,165};
	private static final boolean [] RENDER_SUN_CONTOUR_ZENITH_ANGLES = {true,true,true,true,true,true,true,true,true,true,false,false,false,false};
	private static final boolean [] RENDER_MOON_CONTOUR_ZENITH_ANGLES = {true,true,true,true,true,true,false,false,false,false,false,false,false,false};
	private static final int [] DAYNIGHT_CONTOUR_ZENITH_ANGLES = {90, 114};
	private static final double MARGIN_FRACTION = 0.005;
	private static final double CIRCUMFERENCE = 40030.17359191636;
	
	private SequenceGenerator sequenceGenerator = new SequenceGenerator();
	
	private int dstRotateSetting = 2;
	private double dstRotate = 0.0;
	
	private Canvas canvas;
	
	private BoundaryContainerRenderer clockBoundaryRenderer;
	
	private AbstractImageRenderer background;
	private ImageRenderer faceArtwork;
	private ImageRenderer hourHand;
	private ImageRenderer minuteHand;
	private ImageRenderer secondHand;
	
	private ArcRenderer observerLocation;
	
	private PinnableCelestialObject sunObject;
	private PinnableCelestialObject moonObject;
	private PinnableCelestialObject [] planetRenderers;
	
	private BoundaryContainerRenderer starContainerRenderer;
	
	private BoundaryContainerRenderer swissRailwaySecondRenderer;
	private ArcRenderer swissRailwayTipCircle;
	private PolyRenderer swissRailwayNeedle;
	
	private TextRenderer dateRenderer;
	private TextRenderer observerLocationRenderer;
	private TextRenderer subSolarRenderer;
	private TextRenderer sunAltitudeRenderer;
	private TextRenderer sunAzimuthRenderer;
	private TextRenderer subLunarRenderer;
	private TextRenderer moonAltitudeRenderer;
	private TextRenderer moonAzimuthRenderer;
	private TextRenderer moonPhaseRenderer;
	
	private BoundaryContainerRenderer sunContourLinesContainerRenderer;
	private BoundaryContainerRenderer moonContourLinesContainerRenderer;
	private LineRenderer [] sunlightContourLines = new LineRenderer[CONTOUR_ZENITH_ANGLES.length];
	private LineRenderer [] moonlightContourLines = new LineRenderer[CONTOUR_ZENITH_ANGLES.length];
	private BoundaryContainerRenderer sunEclipticContainerRenderer;
	private BoundaryContainerRenderer moonEclipticContainerRenderer;
	
	private LineRenderer userGESunSightLine;
	private LineRenderer userGEMoonSightLine;

	private double sunDayNightLatitude = -99999.0;
	private double sunDayNightLongitude = -99999.0;
	private double moonDayNightLatitude = -99999.0;
	private double moonDayNightLongitude = -99999.0;
	private VirtualImageCanvasRenderer sunDayNightRenderer;
	private VirtualImageCanvasRenderer moonIlluminationRenderer;
	private ColorRenderer [] sunDayNightFillColors;
	private ColorRenderer [] moonDayNightFillColors;
	private Point2D.Double [] sunFillPoints;
	private Point2D.Double [] moonFillPoints;
	private LineRenderer [] sunDayNightContourLines;
	private LineRenderer [] moonDayNightContourLines;
	private RendererI sunFillBoundary;
	private RendererI moonFillBoundary;
	
	private RendererI topRenderer;
	
	private SunPrecession sunPrecession;
	private MoonPrecession moonPrecession;
	
	private boolean railwayStyleClock = true;
	private boolean twentyFourHourClock = true;
	private boolean renderSunGPGreatCircleRoute = true;
	private boolean renderMoonGPGreatCircleRoute = true;
	private int fillLevel;
	
	private int shiftDirection = 0;
	
	private long lastSunDayNightDraw = 0;
	private long lastMoonDayNightDraw = 0;
	
	private boolean showClock = true;
	private boolean showArtwork;
	
	private String clockFace = "mickeymouse";
	
	private MapCenterMode centerMode;
	
	public AbstractCelestialObjects(Canvas canvas) throws Exception {
		this.canvas = canvas;
		postConstructorInit();
	}
	
	protected abstract void postConstructorInit() throws Exception;
	
	protected void init() throws Exception {
		sequenceGenerator.setCalendarField(-1);
		
		observerLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		
		sunObject = new PinnableCelestialObject(this);
		moonObject = new PinnableCelestialObject(this);
		
		planetRenderers = new PinnableCelestialObject[8];
		
		for (int i = 0;i < planetRenderers.length;i ++) {
			planetRenderers[i] = new PinnableCelestialObject(this);
		}
		
		makeSwissRailwaySecondNeedle();
		
        dateRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        dateRenderer.setMaintainAspectRatio(false);
        observerLocationRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        observerLocationRenderer.setMaintainAspectRatio(false);
        subSolarRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        subSolarRenderer.setMaintainAspectRatio(false);
        sunAltitudeRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        sunAltitudeRenderer.setMaintainAspectRatio(false);
        sunAzimuthRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        sunAzimuthRenderer.setMaintainAspectRatio(false);
        subLunarRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        subLunarRenderer.setMaintainAspectRatio(false);
        moonAltitudeRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        moonAltitudeRenderer.setMaintainAspectRatio(false);
        moonAzimuthRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        moonAzimuthRenderer.setMaintainAspectRatio(false);
        moonPhaseRenderer = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
        moonPhaseRenderer.setMaintainAspectRatio(false);
		
		sunPrecession = new SunPrecession(sequenceGenerator.getMyLocation(), true);
		moonPrecession = new MoonPrecession(sequenceGenerator.getMyLocation(), true);

		this.canvas = canvas;
		
		background = getBackgroundImageRenderer();
		background.setRotateAngle(getRotationAngle(getViewRotationAngle()));
		background.setMaintainAspectRatio(true);
		
//		background.setDebug(true);
		
		faceArtwork = new ImageRenderer(new File("clocks/" + clockFace + "/artwork.png"), null);
		faceArtwork.setRotateAngle(getRotationAngle(getViewRotationAngle()));
		
		hourHand = new ImageRenderer(new File("clocks/" + clockFace + "/hour-hand.png"));
		hourHand.setRotateAngle(0.0);
		
		minuteHand = new ImageRenderer(new File("clocks/" + clockFace + "/minute-hand.png"));
		minuteHand.setRotateAngle(0.0);
		
		secondHand = new ImageRenderer(new File("clocks/" + clockFace + "/second-hand.png"));
		secondHand.setRotateAngle(0.0);

		
        List<RendererI> renderers = new ArrayList<RendererI>();
        List<RendererI> clockRenderer = new ArrayList<RendererI>();
        List<RendererI> backgroundRenderer = new ArrayList<RendererI>();
        List<RendererI> foregroundRenderer = new ArrayList<RendererI>();
        
        clockRenderer.add(faceArtwork);
        
        ColorRenderer colorRenderer;
        Color c;
        
        ArrayList<RendererI> dayNightRenderers = new ArrayList<RendererI>();

        sunDayNightRenderer = new VirtualImageCanvasRenderer(this);
//        sunDayNightRenderer.setMaintainAspectRatio(true);
        
        boolean pacmanMode = isLineRenderPacmanMode();
        
        sunDayNightContourLines = new LineRenderer[DAYNIGHT_CONTOUR_ZENITH_ANGLES[1] - DAYNIGHT_CONTOUR_ZENITH_ANGLES[0]];
        sunDayNightFillColors = new ColorRenderer[4];
        sunFillPoints = new Point2D.Double[4];
        for (int idx = 0;idx < sunDayNightContourLines.length;idx ++) {
			sunDayNightContourLines[idx] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
			sunDayNightContourLines[idx].setLineConnectionPacmanMode(pacmanMode);
//        	sunDayNightContourLines[idx].setMaintainAspectRatio(true);
			sunDayNightContourLines[idx].setLineWidth(2.0, LineRenderer.ABSOLUTE_COORDINATES);
        	
        	int fillColour = (idx / 6);
        	if (fillColour >= sunDayNightFillColors.length)
        		fillColour = sunDayNightFillColors.length - 1;

        	if (sunDayNightFillColors[fillColour] == null) {
        		sunDayNightFillColors[fillColour] = new ColorRenderer();
        		sunDayNightFillColors[fillColour].setBackgroundColor(Color.black);
        		sunDayNightFillColors[fillColour].setForegroundColor(Color.black);
        		dayNightRenderers.add(sunDayNightFillColors[fillColour]);
        	}
        	dayNightRenderers.add(sunDayNightContourLines[idx]);
        }
        
        setDayNightFillLevel(224);
        
        double circumferenceSizeFraction = getCircumferenceSizeFraction();
        sunFillBoundary = getFillBoundaryRenderer();
        if (sunFillBoundary != null) {
            colorRenderer = new ColorRenderer();
            colorRenderer.setBackgroundColor(Color.black);
            colorRenderer.setForegroundColor(Color.black);
            dayNightRenderers.add(colorRenderer);
            
	        dayNightRenderers.add(sunFillBoundary);
        }
        
        sunDayNightRenderer.setRenderers(dayNightRenderers);
        
        backgroundRenderer.add(background);

        backgroundRenderer.add(sunDayNightRenderer);


        dayNightRenderers = new ArrayList<RendererI>();
        
        moonDayNightContourLines = new LineRenderer[1];
        moonDayNightFillColors = new ColorRenderer[1];
        moonFillPoints = new Point2D.Double[1];
    	moonDayNightContourLines[0] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
    	moonDayNightContourLines[0].setLineConnectionPacmanMode(pacmanMode);
//    	moonDayNightContourLines[0].setMaintainAspectRatio(true);
    	moonDayNightContourLines[0].setLineWidth(2.0, LineRenderer.ABSOLUTE_COORDINATES);
    	
		moonDayNightFillColors[0] = new ColorRenderer();
		moonDayNightFillColors[0].setBackgroundColor(Color.darkGray);
		moonDayNightFillColors[0].setForegroundColor(Color.darkGray);
		dayNightRenderers.add(moonDayNightFillColors[0]);
    	dayNightRenderers.add(moonDayNightContourLines[0]);

    	
        moonFillBoundary = getFillBoundaryRenderer();
        if (moonFillBoundary != null) {
            colorRenderer = new ColorRenderer();
            colorRenderer.setBackgroundColor(Color.white);
            colorRenderer.setForegroundColor(Color.white);
            dayNightRenderers.add(colorRenderer);
            
	        dayNightRenderers.add(moonFillBoundary);
        }
        
        moonIlluminationRenderer = new VirtualImageCanvasRenderer(this);
//        moonIlluminationRenderer.setMaintainAspectRatio(true);
        moonIlluminationRenderer.setRenderers(dayNightRenderers);
        
        backgroundRenderer.add(moonIlluminationRenderer);
        

        sunContourLinesContainerRenderer = new BoundaryContainerRenderer();
//        sunContourLinesContainerRenderer.setMaintainAspectRatio(true);
        
        moonContourLinesContainerRenderer = new BoundaryContainerRenderer();
 //       moonContourLinesContainerRenderer.setMaintainAspectRatio(true);
        
        backgroundRenderer.add(sunContourLinesContainerRenderer);
        backgroundRenderer.add(moonContourLinesContainerRenderer);
        List<RendererI> sunContourLinesRenderers = new ArrayList<RendererI>();
        List<RendererI> moonContourLinesRenderers = new ArrayList<RendererI>();
        

		for (int i = 0;i < CONTOUR_ZENITH_ANGLES.length;i ++) {
			if (CONTOUR_ZENITH_ANGLES[i] == 15) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.white);
		        colorRenderer.setForegroundColor(Color.white);
		        sunContourLinesRenderers.add(colorRenderer);
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.darkGray);
		        colorRenderer.setForegroundColor(Color.darkGray);
		        moonContourLinesRenderers.add(colorRenderer);
			}
			else if (CONTOUR_ZENITH_ANGLES[i] == 90) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.yellow);
		        colorRenderer.setForegroundColor(Color.yellow);
		        sunContourLinesRenderers.add(colorRenderer);
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.blue);
		        colorRenderer.setForegroundColor(Color.blue);
		        moonContourLinesRenderers.add(colorRenderer);
			}
			else if ((CONTOUR_ZENITH_ANGLES[i] == 96) || (CONTOUR_ZENITH_ANGLES[i] == 102) || (CONTOUR_ZENITH_ANGLES[i] == 108)) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.red);
		        colorRenderer.setForegroundColor(Color.red);
		        sunContourLinesRenderers.add(colorRenderer);
		        moonContourLinesRenderers.add(colorRenderer);
			}
			else if ((CONTOUR_ZENITH_ANGLES[i] == 105) || (CONTOUR_ZENITH_ANGLES[i] == 120)) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.black);
		        colorRenderer.setForegroundColor(Color.black);
		        sunContourLinesRenderers.add(colorRenderer);
		        moonContourLinesRenderers.add(colorRenderer);
			}			
			
			sunlightContourLines[i] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
	    	sunlightContourLines[i].setLineConnectionPacmanMode(pacmanMode);
			sunlightContourLines[i].setRenderComponent(RENDER_SUN_CONTOUR_ZENITH_ANGLES[i]);
			sunContourLinesRenderers.add(sunlightContourLines[i]);
			
			moonlightContourLines[i] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
	    	moonlightContourLines[i].setLineConnectionPacmanMode(pacmanMode);
			moonlightContourLines[i].setRenderComponent(RENDER_MOON_CONTOUR_ZENITH_ANGLES[i]);
			moonContourLinesRenderers.add(moonlightContourLines[i]);
		}
        
        sunContourLinesContainerRenderer.setRenderers(sunContourLinesRenderers);
        moonContourLinesContainerRenderer.setRenderers(moonContourLinesRenderers);

        

        clockRenderer.add(hourHand);
        clockRenderer.add(minuteHand);
        clockRenderer.add(secondHand);
        clockRenderer.add(swissRailwaySecondRenderer);
        


      	colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.gray);
        colorRenderer.setForegroundColor(Color.gray);
        foregroundRenderer.add(colorRenderer);

        starContainerRenderer = new BoundaryContainerRenderer();
        starContainerRenderer.setRenderComponent(false);
        foregroundRenderer.add(starContainerRenderer);

        colorRenderer = new ColorRenderer();
        c = PinnableCelestialObject.brighten(Color.yellow, 2.0/3.0);
        colorRenderer.setBackgroundColor(c);
        colorRenderer.setForegroundColor(c);
        foregroundRenderer.add(colorRenderer);
        sunEclipticContainerRenderer = new BoundaryContainerRenderer();
//      sunEclipticContainerRenderer.setMaintainAspectRatio(true);
        sunEclipticContainerRenderer.setRenderComponent(false);;
        foregroundRenderer.add(sunEclipticContainerRenderer);
        
        sunObject.configure(Color.yellow, Color.black, foregroundRenderer);

        
        colorRenderer = new ColorRenderer();
        c = PinnableCelestialObject.brighten(Color.blue, 2.0/3.0);
        colorRenderer.setBackgroundColor(c);
        colorRenderer.setForegroundColor(c);
        foregroundRenderer.add(colorRenderer);
        moonEclipticContainerRenderer = new BoundaryContainerRenderer();
//      moonEclipticContainerRenderer.setMaintainAspectRatio(true);
        moonEclipticContainerRenderer.setRenderComponent(false);;
        foregroundRenderer.add(moonEclipticContainerRenderer);
//      moonEclipticContainerRenderer.setDebug(true);

        moonObject.configure(Color.blue, Color.black, foregroundRenderer);

        for (int i = 0;i < planetRenderers.length;i ++) {
        	planetRenderers[i].configure(Color.magenta, Color.black, foregroundRenderer);
		}
        
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.green);
        colorRenderer.setForegroundColor(Color.green);
        foregroundRenderer.add(colorRenderer);
        foregroundRenderer.add(observerLocation);
        
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.yellow);
        colorRenderer.setForegroundColor(Color.yellow);
        foregroundRenderer.add(colorRenderer);
        userGESunSightLine = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
        userGESunSightLine.setLineConnectionPacmanMode(pacmanMode);
        foregroundRenderer.add(userGESunSightLine);

        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.blue);
        colorRenderer.setForegroundColor(Color.blue);
        foregroundRenderer.add(colorRenderer);
        userGEMoonSightLine = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
        userGEMoonSightLine.setLineConnectionPacmanMode(pacmanMode);
        foregroundRenderer.add(userGEMoonSightLine);

        
        clockBoundaryRenderer = new BoundaryContainerRenderer();
		clockBoundaryRenderer.setShiftDirectionX(shiftDirection);
		clockBoundaryRenderer.setBoundMinimumXFraction(MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMaximumXFraction(1.0 - MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMinimumYFraction(MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMaximumYFraction(1.0 - MARGIN_FRACTION);
		clockBoundaryRenderer.setMaintainAspectRatio(true);
		clockBoundaryRenderer.setRenderers(clockRenderer);
		
        colorRenderer = new ColorRenderer();
        c = new Color(224,224,224);
        colorRenderer.setBackgroundColor(c);
        colorRenderer.setForegroundColor(c);
        renderers.add(colorRenderer);
        
        PolyRenderer polyRenderer = new PolyRenderer(PolyRenderer.FRACTIONAL_COORDINATES);
        polyRenderer.setFill(true);
        polyRenderer.setMaintainAspectRatio(false);
        double [] x = {0.0, 1.0, 1.0, 0.0};
        double [] y = {0.0, 0.0, 1.0, 1.0};
        polyRenderer.setX(x);
        polyRenderer.setY(y);
        renderers.add(polyRenderer);

        background.setShiftDirectionX(shiftDirection);
        BoundaryContainerRenderer backgroundBoundaryRenderer = new BoundaryContainerRenderer();
        backgroundBoundaryRenderer.setShiftDirectionX(shiftDirection);
        backgroundBoundaryRenderer.setBoundMinimumXFraction(MARGIN_FRACTION);
        backgroundBoundaryRenderer.setBoundMaximumXFraction(1.0 - MARGIN_FRACTION);
        backgroundBoundaryRenderer.setBoundMinimumYFraction(MARGIN_FRACTION);
        backgroundBoundaryRenderer.setBoundMaximumYFraction(1.0 - MARGIN_FRACTION);
        backgroundBoundaryRenderer.setMaintainAspectRatio(true);
        backgroundBoundaryRenderer.setRenderers(backgroundRenderer);
        
        renderers.add(backgroundBoundaryRenderer);
        renderers.add(clockBoundaryRenderer);
        
        BoundaryContainerRenderer foregroundBoundaryRenderer = new BoundaryContainerRenderer();
        foregroundBoundaryRenderer.setShiftDirectionX(shiftDirection);
        foregroundBoundaryRenderer.setBoundMinimumXFraction(MARGIN_FRACTION);
        foregroundBoundaryRenderer.setBoundMaximumXFraction(1.0 - MARGIN_FRACTION);
        foregroundBoundaryRenderer.setBoundMinimumYFraction(MARGIN_FRACTION);
        foregroundBoundaryRenderer.setBoundMaximumYFraction(1.0 - MARGIN_FRACTION);
        foregroundBoundaryRenderer.setMaintainAspectRatio(true);
        foregroundBoundaryRenderer.setRenderers(foregroundRenderer);
        foregroundBoundaryRenderer.setFollowContainer(backgroundBoundaryRenderer);
        
        renderers.add(foregroundBoundaryRenderer);
        
        topRenderer = getTopLevelRenderer();
        
        if (topRenderer != null)
        	renderers.add(topRenderer);

        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.black);
        colorRenderer.setForegroundColor(Color.black);
        renderers.add(colorRenderer);
        renderers.add(dateRenderer);
        renderers.add(observerLocationRenderer);
        renderers.add(subSolarRenderer);
        renderers.add(sunAltitudeRenderer);
        renderers.add(sunAzimuthRenderer);
        renderers.add(subLunarRenderer);
        renderers.add(moonAltitudeRenderer);
        renderers.add(moonAzimuthRenderer);
        renderers.add(moonPhaseRenderer);
        
        int textY = 20;
        
        dateRenderer.setX(10);
        dateRenderer.setY(textY);
        dateRenderer.setText("---");
        textY += 15;
        
        observerLocationRenderer.setX(10);
        observerLocationRenderer.setY(textY);
        observerLocationRenderer.setText("---");
        textY += 15;
        
        subSolarRenderer.setX(10);
        subSolarRenderer.setY(textY);
        subSolarRenderer.setText("---");
        textY += 15;
        
        sunAltitudeRenderer.setX(35);
        sunAltitudeRenderer.setY(textY);
        sunAltitudeRenderer.setText("---");
        textY += 15;
        
        sunAzimuthRenderer.setX(35);
        sunAzimuthRenderer.setY(textY);
        sunAzimuthRenderer.setText("---");
        textY += 15;
        
        subLunarRenderer.setX(10);
        subLunarRenderer.setY(textY);
        subLunarRenderer.setText("---");
        textY += 15;
        
        moonAltitudeRenderer.setX(35);
        moonAltitudeRenderer.setY(textY);
        moonAltitudeRenderer.setText("---");
        textY += 15;
        
        moonAzimuthRenderer.setX(35);
        moonAzimuthRenderer.setY(textY);
        moonAzimuthRenderer.setText("---");
        textY += 15;
        
        moonPhaseRenderer.setX(10);
        moonPhaseRenderer.setY(textY);
        moonPhaseRenderer.setText("---");
        textY += 15;
        
        sunObject.updatePin(0.0, 0.0, false);
        moonObject.updatePin(0.0, 0.0, false);
		for (int i = 0;i < planetRenderers.length;i ++) {
			planetRenderers[i].updatePin(0.0, 0.0, false);
		}
        
        updateLocation(observerLocation, 0, 0);
        
        setRailwayStyleClock(railwayStyleClock);
        
    	faceArtwork.setRenderComponent(isDisplayClock());
    	hourHand.setRenderComponent(isDisplayClock());
    	minuteHand.setRenderComponent(isDisplayClock());
    	secondHand.setRenderComponent(isDisplayClock());
        
    	centerMode = MapCenterMode.OBSERVER_LON; //.OBSERVER_LAT_LON;
    	
        canvas.setRenderers(renderers);
	}
	
	private boolean isDrawBoundary() {
		return ((sunFillBoundary != null) || (moonFillBoundary != null));
	}

	protected void reevaluateComponentSizes() {
		if (isDrawBoundary()) {
			double circumferenceSizeFraction = getCircumferenceSizeFraction();
			setRendererSizeFraction(sunFillBoundary, circumferenceSizeFraction);
			setRendererSizeFraction(moonFillBoundary, circumferenceSizeFraction);
		
			sunDayNightLongitude = -99999.99;
			moonDayNightLongitude = -99999.99;
        }		
	}
	
	protected abstract void setRendererSizeFraction(RendererI renderer, double fraction);
	
	public void setFullSize(boolean fullSize) {
	}
	
	public boolean isFullSize() {
		return true;
	}
	
	public void setCenterLocation(MapCenterMode centerMode) {
		MapCenterMode lastMode = this.centerMode;
		this.centerMode = centerMode;
		if (lastMode != centerMode)
			reevaluateComponentSizes();
	}
	
	public MapCenterMode getCenterLocation() {
		return centerMode;
	}
	
	public boolean isDisplayClock() {
		if (! showClock)
			return false;
		
		return isShowAnalogClock();
	}
	
	protected AbstractImageRenderer getBackgroundImageRenderer() {
		return new ImageRenderer(new File("projections/" + getProjection() + "/map.png"), null);
	}
	
	protected Canvas getCanvas() {
		return canvas;
	}

	protected abstract RendererI getFillBoundaryRenderer() throws Exception;
	
	protected RendererI getTopLevelRenderer() {
		return null;
	}
	
	protected abstract boolean isLineRenderPacmanMode();
	protected abstract double getCircumferenceSizeFraction();
	protected abstract String getProjection();
	protected abstract boolean isShowAnalogClock();
	
	public void runScript() {
		sequenceGenerator.loadFromScript(new File("script.txt"));
	}
	
	public void abortScript() {
		sequenceGenerator.abortScript();
	}
	
	public boolean isRunningScript() {
		return sequenceGenerator.isRunningScript();
	}
	
	public void setTimeBias(int timeBiasSeconds) {
		sequenceGenerator.getTime().setTimeBiasMillis(timeBiasSeconds * 1000);
	}

	public void setClockFace(boolean mickeyFace) {
		if (mickeyFace) 
			clockFace = "mickeymouse";
		else
			clockFace = "needle-hand";
		
		File artworkFile = new File("clocks/" + clockFace + "/artwork.png");
		if (artworkFile.exists()) {
			faceArtwork.reinitOverlay(artworkFile);
			faceArtwork.setRenderComponent(true);
			showArtwork = true;
		} else {
			faceArtwork.setRenderComponent(false);
			showArtwork = false;
		}
		
		hourHand.reinitImage(new File("clocks/" + clockFace + "/hour-hand.png"));
		minuteHand.reinitImage(new File("clocks/" + clockFace + "/minute-hand.png"));
		secondHand.reinitImage(new File("clocks/" + clockFace + "/second-hand.png"));
	}
	
	public int getShiftDirection() {
		return shiftDirection;
	}

	public void setShiftDirection(int shiftDirection) {
		if (shiftDirection < -1) {
			shiftDirection = -1;
		} else if (shiftDirection > 1) {
			shiftDirection = 1;
		}
		this.shiftDirection = shiftDirection;
		clockBoundaryRenderer.setShiftDirectionX(shiftDirection);
	}

	public void setSunRenderContourLines(boolean contourLines) {
		sunContourLinesContainerRenderer.setRenderComponent(contourLines);
	}
	
	public boolean isSunRenderContourLines() {
		return sunContourLinesContainerRenderer.isRenderComponent();
	}
	
	public void setMoonRenderContourLines(boolean contourLines) {
		moonContourLinesContainerRenderer.setRenderComponent(contourLines);
	}
	
	public boolean isMoonRenderContourLines() {
		return moonContourLinesContainerRenderer.isRenderComponent();
	}
	
	public void setDayNightRendered(boolean rendered) {
		sunDayNightRenderer.setRenderComponent(rendered);
		sunDayNightLatitude = -99999.99;
	}
	
	public boolean isDayNightRendered() {
		return isDayNightRendered(sunDayNightRenderer);
	}
	
	public void setMoonDayNightRendered(boolean rendered) {
		moonDayNightLatitude = -99999.99;
		moonIlluminationRenderer.setRenderComponent(rendered);
		moonDayNightLatitude = -99999.99;
	}
	
	public boolean isMoonDayNightRendered() {
		return isDayNightRendered(moonIlluminationRenderer);
	}
	
	private boolean isDayNightRendered(VirtualImageCanvasRenderer dayNightRenderer) {
		return dayNightRenderer.isRenderComponent();
	}
	
	public void setDayNightFillLevel(int fillLevel) {
		this.fillLevel = fillLevel;
		
		int maxValue = fillLevel;
		if (maxValue > 255)
			maxValue = 255;
		int variableLevels = maxValue - 96 + 1;
		

        for (int fillColour = 0;fillColour < sunDayNightFillColors.length;fillColour ++) {
            int colorIdx = 96 + variableLevels * (fillColour * 6) / ((sunDayNightFillColors.length - 1) * 6);
            if (colorIdx > maxValue)
            	colorIdx = maxValue;
            Color c = new Color(colorIdx, colorIdx, colorIdx);
            sunDayNightFillColors[fillColour].setBackgroundColor(c);
            sunDayNightFillColors[fillColour].setForegroundColor(c);
        }
        
        invalidateDayNight();
	}
	
	public int getDayNightFillLevel() {
		return fillLevel;
	}
	
	public static String [] getPrecessionPathModeLabels() {
		String [] s = {"Off", "Ecliptic", "Analemma"};
		return s;
	}
	
	public void setSunPrecessionPathMode(int mode) {
		if (mode == 0) {
			sunEclipticContainerRenderer.setRenderComponent(false);
		} else if (mode == 1) {
			sunEclipticContainerRenderer.setRenderComponent(true);
			sunPrecession.setShowAsAnalemma(false);
		} else if (mode == 2) {
			sunEclipticContainerRenderer.setRenderComponent(true);
			sunPrecession.setShowAsAnalemma(true);
		}
	}
	
	public int getSunPrecessionPathMode() {
		if (! sunEclipticContainerRenderer.isRenderComponent())
			return 0;
		
		if (! sunPrecession.isShowAsAnalemma())
			return 1;
		
		return 2;
	}
	
	public void setMoonPrecessionPathMode(int mode) {
		if (mode == 0) {
			moonEclipticContainerRenderer.setRenderComponent(false);
		} else if (mode == 1) {
			moonEclipticContainerRenderer.setRenderComponent(true);
			moonPrecession.setShowAsAnalemma(false);
		} else if (mode == 2) {
			moonEclipticContainerRenderer.setRenderComponent(true);
			moonPrecession.setShowAsAnalemma(true);
		}
	}
	
	public int getMoonPrecessionPathMode() {
		if (! moonEclipticContainerRenderer.isRenderComponent())
			return 0;
		
		if (! moonPrecession.isShowAsAnalemma())
			return 1;
		
		return 2;
	}
	
	private void updateSwissRailwaySecondNeedle(double angle) {
		if (! swissRailwaySecondRenderer.isRenderComponent())
			return;
		
		double angleRad = Math.toRadians(angle);
		
		double yLength = 0.1875;
		Point2D.Double p = rotatePoint(0.5, yLength, angleRad);
		swissRailwayTipCircle.setX(p.x);
		swissRailwayTipCircle.setY(p.y);
		
		double width = 0.00625;
		double [] x = {0.5 - width, 0.5 + width, 0.5 + width, 0.5 - width};
		double [] y = {yLength, yLength, 0.55, 0.55};
		
		for (int i = 0;i < x.length;i ++) {
			p = rotatePoint(x[i], y[i], angleRad);
			x[i] = p.x;
			y[i] = p.y;
		}
		swissRailwayNeedle.setX(x);
		swissRailwayNeedle.setY(y);
	}
	
	private void makeSwissRailwaySecondNeedle() throws Exception {
		swissRailwayTipCircle = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.FRACTIONAL_COORDINATES);
		Point2D.Double p2d = updateLocation(-45.0, -180.0, 0);
		swissRailwayTipCircle.setArcAngle(360);
		swissRailwayTipCircle.setMaintainAspectRatio(true);
		swissRailwayTipCircle.setFill(true);
		swissRailwayTipCircle.setWidth(0.0375);
		swissRailwayTipCircle.setHeight(0.0375);
		
		swissRailwayNeedle = new PolyRenderer(PolyRenderer.FRACTIONAL_COORDINATES);
		swissRailwayNeedle.setFill(true);
		swissRailwayNeedle.setMaintainAspectRatio(true);
		
		List<RendererI> renderers = new ArrayList<RendererI>();
		
		ColorRenderer colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.red);
        colorRenderer.setForegroundColor(Color.red);
		renderers.add(colorRenderer);
		renderers.add(swissRailwayTipCircle);
		renderers.add(swissRailwayNeedle);
		
		swissRailwaySecondRenderer = new BoundaryContainerRenderer();
		swissRailwaySecondRenderer.setMaintainAspectRatio(true);
		swissRailwaySecondRenderer.setRenderers(renderers);
		
		updateSwissRailwaySecondNeedle(0.0);
	}

	public void updateObjects() throws Exception {
		updateObjects(true);
	}
	
	protected abstract void setClockLHA(double localHourAngle);
	protected abstract boolean showObjectsAsPins();

	protected double getViewRotationAngle() {
		if ((centerMode == MapCenterMode.OBSERVER_LAT_LON) || (centerMode == MapCenterMode.OBSERVER_LON)) {
			rotateToCoordinate((centerMode == MapCenterMode.OBSERVER_LAT_LON) ? sequenceGenerator.getMyLocation().getLatitude() : 0.0, sequenceGenerator.getMyLocation().getLongitude());
			return sequenceGenerator.getMyLocation().getLongitude();
		}
		
		if (centerMode == MapCenterMode.SUN) {
			AbstractCelestialObject solarObjects = sequenceGenerator.getSolarObjects();
			solarObjects.setObjectIndex(0);
			rotateToCoordinate(solarObjects.getEarthPositionOverhead().getLatitude(), solarObjects.getEarthPositionOverhead().getLongitude());
			return solarObjects.getEarthPositionOverhead().getLongitude();
		}
		
		if (centerMode == MapCenterMode.MOON) {
			rotateToCoordinate(sequenceGenerator.getMoon().getEarthPositionOverhead().getLatitude(), sequenceGenerator.getMoon().getEarthPositionOverhead().getLongitude());
			return sequenceGenerator.getMoon().getEarthPositionOverhead().getLongitude();
		}
		
		return 0.0;
	}
	
	public void updateObjects(boolean repaintCanavas) throws Exception {
        int backgroundRotateDirection = getBackgroundRotateDirection();
        double clockRotate;
        
        if (backgroundRotateDirection >= 0)
        	clockRotate = 360.0;
        else
        	clockRotate = -360.0;
        
        boolean pacmanMode = isLineRenderPacmanMode();
        
        if (sequenceGenerator.isInitialized()) {
        	boolean dstChanged = sequenceGenerator.advanceSequence(dstRotateSetting == 2);
        	
    		Calendar cal = sequenceGenerator.getTime().getTime();
    		
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
			
			if (sequenceGenerator.getCalendarField() == -1)
				milliSecond = 0;
			
			if (dstChanged) {
				sunDayNightLongitude = -99999.99;
				moonDayNightLongitude = -99999.99;
			}
			
			if (dstRotateSetting == 1)
				dstRotate = tdstOffset;
			
			
			double seconds = second + (milliSecond / 1000.0);
			double minutesSeconds = minute + (seconds / 60.0);
			double hoursMinutesSeconds = hour + (minutesSeconds / 60.0);
			
			if (railwayStyleClock) {
				seconds *= 1.0256410256410256410;
				
				if (seconds > 59.999)
					seconds = 0.0;
			}
		
			if (railwayStyleClock) {
				minutesSeconds = minute;
				hoursMinutesSeconds = hour + (minutesSeconds / 60.0);
			}
			
			if (hoursMinutesSeconds >= 24.0)
				hoursMinutesSeconds -= 24.0;
			
			double hoursPerRevolution;
			
			if (twentyFourHourClock)
				hoursPerRevolution = 24.0;
			else
				hoursPerRevolution = 12.0;
			
			setClockLHA(clockRotate * hoursMinutesSeconds / 24.0);

			hourHand.setRotateAngle(clockRotate * hoursMinutesSeconds / hoursPerRevolution);
			minuteHand.setRotateAngle(clockRotate * (minutesSeconds / 60.0));
			double secondsAngle = getViewRotationAngle() - (360.0 * (seconds / 60.0));
			secondHand.setRotateAngle(clockRotate * (seconds / 60.0));
			updateSwissRailwaySecondNeedle(secondsAngle);
			
			String subSecond = "";
			if (sequenceGenerator.getCalendarField() == 0)
				subSecond = String.format(".%03d", milliSecond);
			
    		dateRenderer.setText(String.format("%04d-%02d-%02d %02d:%02d:%02d%s %4.1f", year, month, day, hour, minute, second, subSecond, timezoneAdjust));

    		observerLocationRenderer.setText("Observer lat: " + sequenceGenerator.getMyLocation().getLatitude() + ", lon: " + sequenceGenerator.getMyLocation().getLongitude());
    		
			AbstractCelestialObject solarObjects = sequenceGenerator.getSolarObjects();
			solarObjects.setObjectIndex(0);
    		GeoLocation subObjectPoint;
    		subObjectPoint = solarObjects.getEarthPositionOverhead();
    		
    		boolean showAsPins = showObjectsAsPins();
    		
    		sunObject.updatePin(subObjectPoint.getLatitude(), subObjectPoint.getLongitude(), 16, 12, 6, showAsPins);
    		double sunRa = solarObjects.getCurrentDirection().getRightAscension();
    		
    		subSolarRenderer.setText("Sub-Solar coordinate: " + subObjectPoint.getLatitude() + " " + subObjectPoint.getLongitude());
    		updateContourLines(sunDayNightRenderer, sunlightContourLines, sunDayNightFillColors, sunDayNightContourLines, sunFillPoints, subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
    		
    		ObjectDirectionAltAz altitudeAzimuth = solarObjects.getCurrentDirectionAsAltitudeAzimuth();
    		sunAltitudeRenderer.setText("Altitude: " + altitudeAzimuth.getAltitude());
    		sunAzimuthRenderer.setText("Azimuth: " + altitudeAzimuth.getAzimuth());
            
    		ArrayList<GeoLocation> pathToObject = null;
    		if ((renderSunGPGreatCircleRoute) && (altitudeAzimuth.getAltitude() >= 0))
    			pathToObject = GeoCalculator.divideGreatCircle(sequenceGenerator.getMyLocation(), subObjectPoint, 360);
    		setLineRendererPathBetweenTwoPoints(userGESunSightLine, pathToObject);
    		
    		for (int i = 0;i < planetRenderers.length;i ++) {
    			solarObjects.setObjectIndex(i + 1);
        		subObjectPoint = solarObjects.getEarthPositionOverhead();
        		planetRenderers[i].updatePin(subObjectPoint.getLatitude(), subObjectPoint.getLongitude(), 10, 8, 4, showAsPins);
    		}
    		
    		
    		CelestialObject moon = sequenceGenerator.getMoon();
    		subObjectPoint = moon.getEarthPositionOverhead();
    		moonObject.updatePin(subObjectPoint.getLatitude(), subObjectPoint.getLongitude(), 16, 12, 6, showAsPins);
    		
    		double moonRa = moon.getCurrentDirection().getRightAscension();
    		
    		subLunarRenderer.setText("Sub-Lunar coordinate: " + subObjectPoint.getLatitude() + " " + subObjectPoint.getLongitude());
    		updateContourLines(moonIlluminationRenderer, moonlightContourLines, moonDayNightFillColors, moonDayNightContourLines, moonFillPoints, subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
    		
    		altitudeAzimuth = moon.getCurrentDirectionAsAltitudeAzimuth();
    		moonAltitudeRenderer.setText("Altitude: " + altitudeAzimuth.getAltitude());
    		moonAzimuthRenderer.setText("Azimuth: " + altitudeAzimuth.getAzimuth());
    		
    		pathToObject = null;
    		if ((renderMoonGPGreatCircleRoute) && (altitudeAzimuth.getAltitude() >= 0))
    			pathToObject = GeoCalculator.divideGreatCircle(sequenceGenerator.getMyLocation(), subObjectPoint, 360);
    		setLineRendererPathBetweenTwoPoints(userGEMoonSightLine, pathToObject);
    		
			double moonSunDelta = ((720.0 + sunRa - moonRa) % 360.0);
			String moonPhase;
			double moonPct = ((1 - Math.cos(Math.toRadians(moonSunDelta))) * 50.0);
			if (moonSunDelta < 180.0) {
				moonPhase = moonPct + "% waxing";
			} else {
				moonPhase = moonPct + "% waning";
			}
			moonPhaseRenderer.setText("Moon phase: " + moonPhase);
			
			int colorValue = (int)(0.85 * moonPct);
			Color c = new Color(colorValue, colorValue, colorValue);
			moonDayNightFillColors[0].setBackgroundColor(c);
			moonDayNightFillColors[0].setForegroundColor(c);

        }
        
        List<CelestialObject> stars = sequenceGenerator.getStars();
        List<RendererI> starRenderers = new ArrayList<RendererI>();
        for (CelestialObject star : stars) {
        	ArcRenderer starLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
        	updateLocation(starLocation, star.getEarthPositionOverhead().getLatitude(), star.getEarthPositionOverhead().getLongitude(), 3);
        	starRenderers.add(starLocation);
        }
        starContainerRenderer.setRenderers(starRenderers);
        starContainerRenderer.setRenderComponent(starRenderers.size() > 0);
        
        if (sunEclipticContainerRenderer.isRenderComponent()) {
	        List<PrecessionData> precessionTimes = sunPrecession.calculatePrecession(sequenceGenerator.getTime());
	        List<RendererI> precessionRenderers = new ArrayList<RendererI>();
	        try {
		        for (PrecessionData data : precessionTimes) {
		        	ArcRenderer precessionLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		        	updateLocation(precessionLocation, data.getGroundPosition().getLatitude(), data.getGroundPosition().getLongitude(), 3);
		        	precessionRenderers.add(precessionLocation);
		        }
	        } catch (Exception e) {
	        }
	        sunEclipticContainerRenderer.setRenderers(precessionRenderers);
        }
        
        if (moonEclipticContainerRenderer.isRenderComponent()) {
	        List<PrecessionData> precessionTimes = moonPrecession.calculatePrecession(sequenceGenerator.getTime());
	        List<RendererI> precessionRenderers = new ArrayList<RendererI>();
	        LineRenderer lines = null;
	        try {
		        lines = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
		        lines.setLineConnectionPacmanMode(pacmanMode);
		        lines.setMaintainAspectRatio(true);
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
	        
			ArrayList<Double> fractionX1 = new ArrayList<Double>();
			ArrayList<Double> fractionY1 = new ArrayList<Double>();
			ArrayList<Double> fractionX2 = new ArrayList<Double>();
			ArrayList<Double> fractionY2 = new ArrayList<Double>();

			Double lastX = null;
			Double lastY = null;

			boolean breakContourLines = allowBrokenContourLines();

			try {
		        for (PrecessionData data : precessionTimes) {
		        	ArcRenderer precessionLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
//		        	precessionLocation.setDebug(true);
		        	updateLocation(precessionLocation, data.getGroundPosition().getLatitude(), data.getGroundPosition().getLongitude(), 3);
		        	if (precessionLocation.isRenderComponent()) {
			        	precessionRenderers.add(precessionLocation);
			        	
						if ((lastX != null) && (lastY != null)) {
				        	fractionX1.add(lastX);
							fractionY1.add(lastY);
							fractionX2.add(precessionLocation.getX());
							fractionY2.add(precessionLocation.getY());
						}
	
			        	lastX = precessionLocation.getX();
			        	lastY = precessionLocation.getY();
		        	} else {
						if (breakContourLines) {
							lastX = lastY = null;
						}
		        	}
		        }
	        } catch (Exception e) {
	        }
	        
	        lines.setX1Array(arrayListToArray(fractionX1));
	        lines.setY1Array(arrayListToArray(fractionY1));
	        lines.setX2Array(arrayListToArray(fractionX2));
	        lines.setY2Array(arrayListToArray(fractionY2));
	        precessionRenderers.add(lines);
	        
	        moonEclipticContainerRenderer.setRenderers(precessionRenderers);
        }
        
        if (backgroundRotateDirection != 0)
        	background.setRotateAngle(((double)backgroundRotateDirection) * getRotationAngle(getViewRotationAngle()) + (dstRotate * 15.0));
        
        updateLocation(observerLocation, sequenceGenerator.getMyLocation().getLatitude(), sequenceGenerator.getMyLocation().getLongitude());
        
        if (repaintCanavas)
        	canvas.repaint();
	}
	
	protected abstract boolean allowBrokenContourLines();
	protected abstract int getBackgroundRotateDirection();
	
	protected SequenceGenerator getSequenceGenerator() {
		return sequenceGenerator;
	}
	
	protected double getDstRotate() {
		return dstRotate;
	}

	private void setLineRendererPathBetweenTwoPoints(LineRenderer lineRenderer, ArrayList<GeoLocation> pathToObject) {
		ArrayList<Double> fractionX1 = new ArrayList<Double>();
		ArrayList<Double> fractionY1 = new ArrayList<Double>();
		ArrayList<Double> fractionX2 = new ArrayList<Double>();
		ArrayList<Double> fractionY2 = new ArrayList<Double>();
		
		if ((pathToObject == null) || (pathToObject.size() < 2)) {
			lineRenderer.setRenderComponent(false);
			return;
		}
			
		Double lastX = null;
		Double lastY = null;
		
		for (GeoLocation location : pathToObject) {
			Point2D.Double locatePoint = updateLocation(location.getLatitude(), location.getLongitude());
			
			if (locatePoint != null) {
				if ((lastX != null) && (lastY != null)) {
					fractionX1.add(lastX);
					fractionY1.add(lastY);
					fractionX2.add(locatePoint.x);
					fractionY2.add(locatePoint.y);
				}
	
				lastX = locatePoint.x;
				lastY = locatePoint.y;
			}
		}
		
		lineRenderer.setRenderComponent(true);
		lineRenderer.setX1Array(arrayListToArray(fractionX1));
		lineRenderer.setY1Array(arrayListToArray(fractionY1));
		lineRenderer.setX2Array(arrayListToArray(fractionX2));
		lineRenderer.setY2Array(arrayListToArray(fractionY2));
	}
	
	public int getDstRotateSetting() {
		return dstRotateSetting;
	}

	public void setDstRotateSetting(int dstRotateSetting) {
		this.dstRotateSetting = dstRotateSetting;
	}

	public void speedDown() {
		sequenceGenerator.speedDown();
	}
	
	public void speedUp() {
		sequenceGenerator.speedUp();
	}
	
	public void setSpeed(int speedIndex) {
		sequenceGenerator.setSpeed(speedIndex);
	}
	
	public int getCurrentSpeed() {
		return sequenceGenerator.getCurrentSpeed();
	}
	
	public static String [] getSpeedLabels() {
		return SequenceGenerator.getSpeedLabels();
	}
	
	private Point2D.Double rotatePoint(double x, double y, double angle) {
		double xFrac = x - 0.5;
		double yFrac = 0.5 - y;
		double radius = Math.sqrt(xFrac * xFrac + yFrac * yFrac);
		
		double originalAngle = Math.atan2(yFrac, xFrac);
		
		return rotatePoint(radius, originalAngle + angle - Math.toRadians(getViewRotationAngle()));
	}
	
	protected Point2D.Double rotatePoint(double radius, double angle) {
		double fractionX = radius * Math.cos(angle) + 0.5;
		double fractionY = 0.5 - radius * Math.sin(angle);
		
		return new Point2D.Double(fractionX, fractionY);
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
	
	private double getRotationAngle(double longitude) {
		double bias = -90.0;
		
//		System.out.println("??? ROTATION ANGLE??? " + (bias - (-90 - longitude)) + " for " + longitude);
		
		return bias - (-90 - longitude);
	}
	
	protected abstract boolean isDynamicContourLineBearings();
	
	private void updateContourLines(VirtualImageCanvasRenderer dayNightRenderer, LineRenderer [] contourLine, ColorRenderer [] dayNightFillColors, LineRenderer [] dayNightContourLines, Point2D.Double [] fillPoints, double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		
		for (int i = 0;i < CONTOUR_ZENITH_ANGLES.length;i ++) {
			int distanceMarker = CONTOUR_ZENITH_ANGLES[i];
			
			double distance = CIRCUMFERENCE * (double)distanceMarker / 360.0;
			
			ArrayList<Double> fractionX1 = new ArrayList<Double>();
			ArrayList<Double> fractionY1 = new ArrayList<Double>();
			ArrayList<Double> fractionX2 = new ArrayList<Double>();
			ArrayList<Double> fractionY2 = new ArrayList<Double>();
			
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
			
			contourLine[i].setX1Array(arrayListToArray(fractionX1));
			contourLine[i].setY1Array(arrayListToArray(fractionY1));
			contourLine[i].setX2Array(arrayListToArray(fractionX2));
			contourLine[i].setY2Array(arrayListToArray(fractionY2));
		}

		if (dayNightContourLines != null)
			updateDayNight(dayNightRenderer, dayNightContourLines, dayNightFillColors, fillPoints, latitude, longitude);
	}
	
	private void invalidateDayNight() {
		sunDayNightLatitude = moonDayNightLatitude = -999999.99;
	}
	
	public void resetDayNightRender() {
		sunDayNightLatitude = moonDayNightLatitude = -999999999.99;
	}
	
	protected abstract double [] getFillBearings();
	protected abstract double getFillStartBearing();
	
	private void updateDayNight(VirtualImageCanvasRenderer dayNightRenderer, LineRenderer [] dayNightContourLines, ColorRenderer [] dayNightFillColors, Point2D.Double [] fillPoints, double latitude, double longitude) {
		if (! isDayNightRendered(dayNightRenderer))
			return;
		
        int backgroundRotateDirection = getBackgroundRotateDirection();
		if (backgroundRotateDirection != 0)
			dayNightRenderer.setRotateAngle((double)(backgroundRotateDirection) * -longitude);
		
		long thisDrawTime = sequenceGenerator.getTime().getTime().getTimeInMillis();
		long nextDrawTime = thisDrawTime - 10000L;
		
		boolean breakContourLines = allowBrokenContourLines();
		
		if (! sequenceGenerator.isRunningScript()) {
			if (dayNightRenderer == moonIlluminationRenderer) {
				if ((Math.abs(latitude - moonDayNightLatitude) < 0.5) && (Math.abs(longitude - moonDayNightLongitude) < 0.5) &&
						(lastMoonDayNightDraw > nextDrawTime)) {
					dayNightRenderer.invalidate();
	
					return;
				}
				
				moonDayNightLongitude = longitude;
				moonDayNightLatitude = latitude;
				lastMoonDayNightDraw = thisDrawTime;
			} else {
				if ((Math.abs(latitude - sunDayNightLatitude) < 0.5) && (Math.abs(longitude - sunDayNightLongitude) < 0.5) &&
						(lastSunDayNightDraw > nextDrawTime)) {
					dayNightRenderer.invalidate();
	
					return;
				}
				
				sunDayNightLongitude = longitude;
				sunDayNightLatitude = latitude;
				lastSunDayNightDraw = thisDrawTime;
			}
		}
		
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, (backgroundRotateDirection != 0) ? 0.0 : longitude);
		
		double debugRadius = 0.0;
		
		int maxAngles = dayNightContourLines.length;
		
		for (int i = 0;i < maxAngles;i += 6) {
			int distanceMarker = DAYNIGHT_CONTOUR_ZENITH_ANGLES[0] + i;
			int distanceMidMarker = DAYNIGHT_CONTOUR_ZENITH_ANGLES[0] + i + 3;
			
			if (dayNightRenderer == moonIlluminationRenderer)
				distanceMidMarker = 22;
			
			ArrayList<Double> fractionX1 = new ArrayList<Double>();
			ArrayList<Double> fractionY1 = new ArrayList<Double>();
			ArrayList<Double> fractionX2 = new ArrayList<Double>();
			ArrayList<Double> fractionY2 = new ArrayList<Double>();
			
        	int fillColour = (i / 6);
        	if (fillColour >= dayNightFillColors.length)
        		fillColour = dayNightFillColors.length - 1;
        	
        	
			GeoLocation computedCoord2;
			Point2D.Double locatePoint;
			
			double [] fillBearings = getFillBearings();
			
			fillPoints[fillColour] = null;
			
			Point2D.Double fp = null;
			Point2D.Double newFp = null;
			
			for (double fillBearing : fillBearings) {
	        	computedCoord2 = GeoCalculator.getCoordinateFromLocationgAndBearing(location, CIRCUMFERENCE * ((double)distanceMidMarker) / 360.0, fillBearing);
	        	newFp = updateLocation(computedCoord2.getLatitude(), computedCoord2.getLongitude());
	        	if (fp == null)
	        		fp = newFp;
	        	else if (newFp != null) {
	        		double x1 = newFp.x - 0.5;
	        		double y1 = 0.5 - newFp.y;
	        		double x2 = fp.x - 0.5;
	        		double y2 = 0.5 - fp.y;
//	        		if (dayNightRenderer == moonIlluminationRenderer)
//	        			System.out.println(distanceMidMarker + "?? 1 ??? " + x1 + ", " + y1 + " and " + x2 + ", " + y2 + " --> " + Math.sqrt(x1*x1+y1*y1) + " and " + Math.sqrt(x2*x2+y2*y2));
	        		if (Math.sqrt(x1*x1+y1*y1) < Math.sqrt(x2*x2+y2*y2))
	        			fp = newFp;
	        	}
			}
			
			while (dayNightRenderer == moonIlluminationRenderer) {
				distanceMidMarker += 22;
				if (distanceMidMarker >= 90)
					break;
				for (double fillBearing : fillBearings) {
		        	computedCoord2 = GeoCalculator.getCoordinateFromLocationgAndBearing(location, CIRCUMFERENCE * ((double)distanceMidMarker) / 360.0, fillBearing);
		        	newFp = updateLocation(computedCoord2.getLatitude(), computedCoord2.getLongitude());
		        	if (fp == null)
		        		fp = newFp;
		        	else if (newFp != null) {
		        		double x1 = newFp.x - 0.5;
		        		double y1 = 0.5 - newFp.y;
		        		double x2 = fp.x - 0.5;
		        		double y2 = 0.5 - fp.y;
//	        			System.out.println(distanceMidMarker + "?? 2 ??? " + x1 + ", " + y1 + " and " + x2 + ", " + y2 + " --> " + Math.sqrt(x1*x1+y1*y1) + " and " + Math.sqrt(x2*x2+y2*y2));
		        		if (Math.sqrt(x1*x1+y1*y1) < Math.sqrt(x2*x2+y2*y2))
		        			fp = newFp;
		        	}
				}
			}
			
			fillPoints[fillColour] = fp;
			
			for (double fraction = 0.0;fraction < 1.0;fraction += 1.0) {
				double distance = CIRCUMFERENCE * ((double)distanceMarker + fraction) / 360.0;
				
				double shift = getFillStartBearing();
				
				if (isDynamicContourLineBearings())
					shift = -90.0;
				
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
				
				dayNightContourLines[i].setX1Array(arrayListToArray(fractionX1));
				dayNightContourLines[i].setY1Array(arrayListToArray(fractionY1));
				dayNightContourLines[i].setX2Array(arrayListToArray(fractionX2));
				dayNightContourLines[i].setY2Array(arrayListToArray(fractionY2));
			}
		}
		
		dayNightRenderer.invalidate();
	}
	
	private double [] arrayListToArray(ArrayList<Double> arrayList) {
		double [] d = new double[arrayList.size()];
		
		for (int i = 0;i < d.length;i ++) {
			d[i] = arrayList.get(i);
		}
		
		return d;
	}
	
	public int animateSpeedHint() {
		return sequenceGenerator.animateSpeedHint();
	}
	
	protected abstract void locationChanged(double latitude, double longitude);
	
	public void setLatitude(double newLatitude) {
		sequenceGenerator.getMyLocation().setLatitude(newLatitude);
		rotateToCoordinate(sequenceGenerator.getMyLocation().getLatitude(), sequenceGenerator.getMyLocation().getLongitude());
	}
	
	public void setLongitude(double newLongitude) {
		sequenceGenerator.getMyLocation().setLongitude(newLongitude);
		rotateToCoordinate(sequenceGenerator.getMyLocation().getLatitude(), sequenceGenerator.getMyLocation().getLongitude());
	}
	
	private int rotatedDecaLat = -99999999;
	private int rotatedDecaLon = -99999999;
	
	private void rotateToCoordinate(double newLatitude, double newLongitude) {
		int decaLat = (int)(newLatitude * 10);
		int decaLon = (int)(newLongitude * 10);
		
		if ((decaLat == rotatedDecaLat) && (decaLon == rotatedDecaLon))
			return;
		
		rotatedDecaLat = decaLat;
		rotatedDecaLon = decaLon;
		
        int backgroundRotateDirection = getBackgroundRotateDirection();
		if (backgroundRotateDirection != 0)
			background.setRotateAngle(((double)backgroundRotateDirection) * getRotationAngle(newLongitude) + (dstRotate * 15.0));
		locationChanged(newLatitude, newLongitude);
	}

	public boolean isRailwayStyleClock() {
		return railwayStyleClock;
	}
	
	public void setShowClock(boolean showClock) {
		this.showClock = showClock;

		if (! isDisplayClock()) {
			secondHand.setRenderComponent(false);
			swissRailwaySecondRenderer.setRenderComponent(false);
		} else {
			secondHand.setRenderComponent(! railwayStyleClock);
			swissRailwaySecondRenderer.setRenderComponent(railwayStyleClock);		
		}

		if (showArtwork)
			faceArtwork.setRenderComponent(isDisplayClock());
    	hourHand.setRenderComponent(isDisplayClock());
    	minuteHand.setRenderComponent(isDisplayClock());
	}
	
	public boolean isShowClock() {
		return showClock;
	}

	public void setRailwayStyleClock(boolean railwayStyleClock) {
		this.railwayStyleClock = railwayStyleClock;
		
		setShowClock(showClock);
	}
	
	public boolean isTwentyFourHourClock() {
		return twentyFourHourClock;
	}

	public void setTwentyFourHourClock(boolean twentyFourHourClock) {
		this.twentyFourHourClock = twentyFourHourClock;
	}

	public boolean isRenderSunGPGreatCircleRoute() {
		return renderSunGPGreatCircleRoute;
	}

	public void setRenderSunGPGreatCircleRoute(boolean renderSunGPGreatCircleRoute) {
		this.renderSunGPGreatCircleRoute = renderSunGPGreatCircleRoute;
	}

	public boolean isRenderMoonGPGreatCircleRoute() {
		return renderMoonGPGreatCircleRoute;
	}

	public void setRenderMoonGPGreatCircleRoute(boolean renderMoonGPGreatCircleRoute) {
		this.renderMoonGPGreatCircleRoute = renderMoonGPGreatCircleRoute;
	}

	@Override
	public void transformImage(RendererI renderer, BufferedImage image) {
		if ((image == null) || (renderer == null))
			return;
		
		if (renderer == sunDayNightRenderer)
			transformContourLines(sunDayNightRenderer, sunDayNightFillColors, sunFillPoints, image, 0x00000000);
		
		if (renderer == moonIlluminationRenderer)
			transformContourLines(moonIlluminationRenderer, moonDayNightFillColors, moonFillPoints, image, 0x00FFFFFF);
	}
	
	private void transformContourLines(VirtualImageCanvasRenderer renderer, ColorRenderer [] dayNightFillColors, Point2D.Double [] fillPoints, BufferedImage image, int fillRGB) {
		int width = image.getWidth();
		int height = image.getHeight();
		
		double averageRadius = ((width + height) / 4.0) * getCircumferenceSizeFraction();
		
		if ((width < 1) || (height < 1))
			return;
		
		int [] rgbArray = new int[width * height];
		image.getRGB(0, 0, width, height, rgbArray, 0, width);
/*		
		for (int y = 0;y < height;y ++) {
			int cartesianY = y - (height / 2);
			for (int x = 0;x < width;x ++) {
				int cartesianX = x - (width / 2);
				double radius = Math.sqrt(cartesianX*cartesianX+cartesianY*cartesianY);
				int alpha = 0x00FFFFFF;
				int i = y * width + x;
				if (radius <= averageRadius)
					alpha = ((((rgbArray[i] >> 16) & 0xFF) + ((rgbArray[i] >> 8) & 0xFF) + (rgbArray[i] & 0xFF)) / 3) << 24;
				rgbArray[i] = alpha;
			}
		} */
		
		/*
		if (isDrawCircumference()) {
			for (int y = 0;y < height;y ++) {
				int cartesianY = y - (height / 2);
				for (int x = 0;x < width;x ++) {
					int cartesianX = x - (width / 2);
					double radius = Math.sqrt(cartesianX*cartesianX+cartesianY*cartesianY);
					int i = y * width + x;
					if (radius > averageRadius)
//					if (rgbArray[i] == 0x00000000)
//						rgbArray[i] = 0xFF802020;
						rgbArray[i] = 0x00000000;
				}
			}
		} */
		
		for (int i = 0;i < fillPoints.length;i ++) {
			try {
				if (fillPoints[i] != null) {
					double px = fillPoints[i].x;
					double py = fillPoints[i].y;
	
					
					Point p = renderer.convertToCoordinates(px, py, AbstractFractionRenderer.FRACTIONAL_COORDINATES, true);
					Color c = dayNightFillColors[i].getForegroundColor();
					int alpha = (((c.getRed() & 0xFF) + (c.getGreen() & 0xFF) + (c.getBlue() & 0xFF)) / 3) << 24;
					
					alpha += (fillRGB & 0xFFFFFF);
					
					if ((p.x >= 0) && (p.x < width) && (p.y >= 0) && (p.y < height)) {
//						alpha = 0xFFFF3232;
					
//						System.out.println("??? ALPHA FOR " + i + "??? " + Integer.toHexString(alpha));
					
						floodFill(rgbArray, width, height, p.x, p.y, alpha, rgbArray[p.y * width + p.x]);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if (isDrawBoundary()) {
			for (int y = 0;y < height;y ++) {
				int cartesianY = y - (height / 2);
				for (int x = 0;x < width;x ++) {
					int cartesianX = x - (width / 2);
					int i = y * width + x;
					if ((rgbArray[i] != 0x00000000) && (isPixelOutOfBounds(cartesianX, cartesianY, width / 2, height / 2, averageRadius)))
						rgbArray[i] = 0x00000000;
				}
			}
		}

		boolean debugFloodFillSeed = false;
		
		if (debugFloodFillSeed) {
			for (int i = 0;i < fillPoints.length;i ++) {
				try {
					if (fillPoints[i] != null) {
						double px = fillPoints[i].x;
						double py = fillPoints[i].y;
						Point p = renderer.convertToCoordinates(px, py, AbstractFractionRenderer.FRACTIONAL_COORDINATES, true);
						
						for (int dy = -2;dy < 3;dy ++) {
							for (int dx = -2; dx < 3;dx ++) {
								if ((p.x + dx >= 0) && (p.x + dx < width) &&
										(p.y + dy >= 0) && (p.y + dy < height)) {
									int idx = (dy + p.y) * width + (dx + p.x);
									rgbArray[idx] = 0xFFFF80FF;
								}
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		
		image.setRGB(0, 0, width, height, rgbArray, 0, width);
	}
	
	protected abstract boolean isPacmanMode();
	protected abstract boolean isPixelOutOfBounds(int cartesianXCoordinate, int cartesianYCoordinate, int xBoundary, int yBoundary, double averageRadiusBoundary);
	
	private void floodFill(int [] rgbArray, int width, int height, int x, int y, int rgbaValue, int fillFromRgba) {
		boolean pacmanMode = isPacmanMode();
		
		int lastLeftX = -1;
		int lastRightX = -1;
		
		boolean lineFilled = false;
		
		if ((x >= 0) && (x < width) && (y >= 0) && (y < height)) {
			int scanX = x;
			while (scanX >= 0) {
				if (rgbArray[y * width + scanX] == fillFromRgba) {
					rgbArray[y * width + scanX] = rgbaValue;
					lineFilled = true;
					lastLeftX = scanX;
				}
				else
					break;
				scanX --;
				
				if ((pacmanMode) && (scanX < 0))
					scanX = width - 1;
			}
			
			scanX = x + 1;
			while (scanX < width) {
				if (rgbArray[y * width + scanX] == fillFromRgba) {
					rgbArray[y * width + scanX] = rgbaValue;
					lineFilled = true;
					lastRightX = scanX;
				}
				else
					break;
				scanX ++;
				
				if ((pacmanMode) && (scanX >= width))
					scanX = 0;
			}
			
			if (lineFilled) {
				if (lastLeftX != -1) {
					scanX = x;
					while (scanX != lastLeftX) {
						if (y > 0) {
							if (rgbArray[(y - 1) * width + scanX] == fillFromRgba) {
								floodFill(rgbArray, width, height, scanX, y - 1, rgbaValue, fillFromRgba);
							}
						}
						
						if (y < height - 1) {
							if (rgbArray[(y + 1) * width + scanX] == fillFromRgba) {
								floodFill(rgbArray, width, height, scanX, y + 1, rgbaValue, fillFromRgba);
							}
						}
	
						scanX --;
						
						if ((pacmanMode) && (scanX < 0))
							scanX = width - 1;
					}
					
					if (lastRightX != -1) {
						scanX = x + 1;
						while (scanX != lastRightX) {
							if (y > 0) {
								if (rgbArray[(y - 1) * width + scanX] == fillFromRgba) {
									floodFill(rgbArray, width, height, scanX, y - 1, rgbaValue, fillFromRgba);
								}
							}
							
							if (y < height - 1) {
								if (rgbArray[(y + 1) * width + scanX] == fillFromRgba) {
									floodFill(rgbArray, width, height, scanX, y + 1, rgbaValue, fillFromRgba);
								}
							}
							
							scanX ++;
							
							if ((pacmanMode) && (scanX >= width))
								scanX = 0;
						}
					}
				}
			}
		}
	}
}
