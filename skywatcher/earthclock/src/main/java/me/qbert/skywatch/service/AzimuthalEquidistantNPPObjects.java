package me.qbert.skywatch.service;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.astro.service.AbstractPrecession.PrecessionData;
import me.qbert.skywatch.astro.service.MoonPrecession;
import me.qbert.skywatch.astro.service.SunPrecession;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;
import me.qbert.skywatch.service.AbstractCelestialObjects.MapCenterMode;
import me.qbert.skywatch.service.projections.AzimuthalEquidistantNPTransform;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.ui.ImageTransformerI;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.AbstractFractionRenderer;
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

public class AzimuthalEquidistantNPPObjects extends AbstractCelestialObjects {
	private AzimuthalEquidistantNPTransform transform;
	private ArcRenderer [] arcRenderers;
	private TextRenderer [] textRenderers;

	private List<RendererI> fullRenderers;
	
	private LineRenderer sunAzimuthLineRenderer;
	
	private double stereoVisionRotate = 0.0;
	
	public AzimuthalEquidistantNPPObjects(Canvas canvas) throws Exception {
		super(canvas);
	}

	public void setStereoVisionRotate(double stereoVisionRotate) {
		this.stereoVisionRotate = stereoVisionRotate;
	}

	@Override
	protected void locationChanged(double latitude, double longitude) {
	}
	
	@Override
	protected boolean showObjectsAsPins() {
		return false;
	}
	
	@Override
	protected boolean isLineRenderPacmanMode() {
		return false;
	}
	
	@Override
	protected void postConstructorInit() throws Exception {
		transform = new AzimuthalEquidistantNPTransform();
		init();
	}
	
	@Override
	protected String getProjection() {
		return "ae-north";
	}
	
	@Override
	protected RendererI getFillBoundaryRenderer() throws Exception {
		ArcRenderer renderer = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.FRACTIONAL_COORDINATES);
		renderer.setArcAngle(360);
		renderer.setFill(false);
		renderer.setMaintainAspectRatio(true);
		renderer.setWidth(getCircumferenceSizeFraction());
		renderer.setHeight(getCircumferenceSizeFraction());
		renderer.setX(0.5);
		renderer.setY(0.5);
		
		return renderer;
	}
	
	@Override
	protected void setRendererSizeFraction(RendererI renderer, double fraction) {
		((ArcRenderer)renderer).setWidth(getCircumferenceSizeFraction());
		((ArcRenderer)renderer).setHeight(getCircumferenceSizeFraction());
	}
	
	@Override
	protected double getCircumferenceSizeFraction() {
		return 1.0;
	}
	
	@Override
	protected void setClockLHA(double localHourAngle) {
	}
	
	@Override
	protected boolean isShowAnalogClock() {
		return true;
	}
	
	@Override
	protected int getBackgroundRotateDirection() {
		return 1;
	}
	
	@Override
	protected boolean allowBrokenContourLines() {
		return false;
	}

	@Override
	protected double [] getFillBearings() {
		double [] bearings = {0.0};
		return bearings;
	}

	@Override
	protected double getFillStartBearing() {
		return 0.0;
	}
	
	@Override
	protected boolean isDynamicContourLineBearings() {
		return true;
	}

	@Override
	public Double updateLocation(double latitude, double longitude) {
		return updateLocation(latitude, longitude, getViewRotationAngle());
	}

	@Override
	public Point2D.Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize) {
		return updateLocation(latitude, longitude, getViewRotationAngle());
	}
	
	
	@Override
	public Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize, double overscan) {
		return updateLocation(latitude, longitude, renderFullCircumferenceSize, 1.0);
	}
	
	@Override
	public Double updateLocation(double latitude, double longitude, double observerLongitude) {
		return transform.transform(latitude, longitude, 0, observerLongitude, (getDstRotate() * 15.0));
	}
	
	public Double updateLocation(double latitude, double longitude, double observerLongitude, double overscan) {
		return transform.transform(latitude, longitude, 0, observerLongitude, (getDstRotate() * 15.0), overscan);
	}
	
	@Override
	protected boolean isPacmanMode() {
		return false;
	}

	@Override
	protected int getPixelOutOfBoundsXForY(int cartesianYCoordinate, int xBoundary, int yBoundary, double averageRadiusBoundary) {
		return (int)Math.sqrt(averageRadiusBoundary*averageRadiusBoundary-cartesianYCoordinate*cartesianYCoordinate);
	}

	@Override
	protected void setRenderers(List<RendererI> renderers) {
		fullRenderers = renderers;
		getCanvas().setRenderers(renderers);
	}

	@Override
	protected void repaintRequest() {
		getCanvas().repaint();
	}

	@Override
	protected Point2D.Double getMoonShadowCoordinate(MapCenterMode centerMode, GeoLocation subSunPoint, GeoLocation subMoonPoint) {
		if (textRenderers.length > 1) {
			for (int i = 0;i < textRenderers.length;i ++)
				textRenderers[i].setRenderComponent(false);
			
//			Point2D.Double newPoint = updateLocation(getSequenceGenerator().getMyLocation().getLatitude(), getSequenceGenerator().getMyLocation().getLongitude());
//			return newPoint;
		}
		
		Point2D.Double sunPoint = updateLocation(subSunPoint.getLatitude(), subSunPoint.getLongitude());
		Point2D.Double moonPoint = updateLocation(subMoonPoint.getLatitude(), subMoonPoint.getLongitude());
		
		double milesPerDegree = 69.49682915;
		double sunHeight = 3000/milesPerDegree;
		double moonHeight = 2904/milesPerDegree;
		
		double sx = (sunPoint.x - 0.5)*180.0;
		double sy = (0.5 - sunPoint.y)*180.0;
		
		double mx = (moonPoint.x - 0.5)*180.0;
		double my = (0.5 - moonPoint.y)*180.0;
		
		
		double newX = ((sx - (sunHeight / ((sunHeight-moonHeight)/(sx-mx))))/180.0) + 0.5;
		double newY = 0.5 - ((sy - (sunHeight / ((sunHeight-moonHeight)/(sy-my))))/180.0);

		if ((newX >= 0) && (newX <= 1.0) && (newY >= 0) && (newY <= 1.0)) {
			Point2D.Double newPoint = new Point2D.Double(newX, newY);
			textRenderers[0].setRenderComponent(true);
			
			newX = newX - 0.5;
			newY = 0.5 - newY;
			
			double extraRotate = getDstRotate() * 15.0;

			double lat = 90 - Math.sqrt(newX*newX + newY*newY)*360;
			double lon = Math.toDegrees(Math.atan2(newY, newX));
			lon = lon + getViewRotationAngle() + 90 + extraRotate;
			
			textRenderers[0].setText("FE eclipse center: " + String.format("%8.3f,%8.3f", lat, lon));
			
			return newPoint;
		}
		
		textRenderers[0].setRenderComponent(false);
		return null;
	}

	@Override
	protected boolean updateUserObject(int userObjectIndex, double latitude, double longitude, double altitude, double diameter) {
		if (userObjectIndex == 5)
			return true;
		
		if ((userObjectIndex >= 0) && (userObjectIndex < arcRenderers.length)) {
			if (latitude < 90) {
				Point2D.Double point = updateLocation(latitude, longitude);
				
				if (stereoVisionRotate != 0) {
					double degrees = (altitude / 69.5);
					double shift = (degrees / 360.0) * Math.tan(Math.toRadians(stereoVisionRotate));
					point.x = point.x + shift;
				}
				
				arcRenderers[userObjectIndex * 2].setX(point.x);
				arcRenderers[userObjectIndex * 2].setY(point.y);
				arcRenderers[userObjectIndex * 2].setRenderComponent(true);
				if (userObjectIndex < 2) {
					arcRenderers[userObjectIndex * 2 + 1].setX(point.x);
					arcRenderers[userObjectIndex * 2 + 1].setY(point.y);
					arcRenderers[userObjectIndex * 2 + 1].setRenderComponent(true);
				}
				
				if (textRenderers.length >= 3) {
					if (userObjectIndex == 0) {
						textRenderers[1].setText("FE sun: " + String.format("%8.3f,%8.3f ; %8.3f ; %8.3f", latitude, longitude, altitude, diameter));
						textRenderers[1].setRenderComponent(true);
					}
					if (userObjectIndex == 1) {
						textRenderers[2].setText("FE moon: " + String.format("%8.3f,%8.3f ; %8.3f ; %8.3f", latitude, longitude, altitude, diameter));
						textRenderers[2].setRenderComponent(true);
					}
				}
	
				return true;
			} else {
				arcRenderers[userObjectIndex * 2].setRenderComponent(false);
				if (userObjectIndex < 2)
					arcRenderers[userObjectIndex * 2 + 1].setRenderComponent(false);
				if (textRenderers.length >= 3) {
					if (userObjectIndex == 0) {
						textRenderers[1].setRenderComponent(false);
					}
					if (userObjectIndex == 1) {
						textRenderers[2].setRenderComponent(false);
					}
				}
				
			}
		}
		
		return false;
	}

	private ArcRenderer createUserObject(double size) throws Exception {
        ArcRenderer newObject = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.FRACTIONAL_COORDINATES);
        newObject.setHeight(size);
        newObject.setWidth(size);
        newObject.setX(0.25);
        newObject.setY(0.5);
        newObject.setArcAngle(360);
        newObject.setFill(true);
        
        return newObject;
	}
	
	@Override
	protected ArrayList<UserObjectSettings> getUserArcRenderObjects() throws Exception {
		arcRenderers = new ArcRenderer[5];
		
		arcRenderers[0] = createUserObject(0.045);
		arcRenderers[0].setRenderComponent(false);
		arcRenderers[1] = createUserObject(0.04);
		arcRenderers[1].setRenderComponent(false);
		arcRenderers[2] = createUserObject(0.055);
		arcRenderers[2].setX(0.75);
		arcRenderers[2].setRenderComponent(false);
		arcRenderers[3] = createUserObject(0.05);
		arcRenderers[3].setX(0.75);
		arcRenderers[3].setRenderComponent(false);
		arcRenderers[4] = createUserObject(0.05);
		arcRenderers[4].setX(0.5);
		arcRenderers[4].setRenderComponent(false);
		
		ArrayList<UserObjectSettings> settings = new ArrayList<UserObjectSettings>();
		UserObjectSettings userObject;
		
		userObject = new UserObjectSettings();
		userObject.userObjectColor = new Color(80, 80, 80);
		userObject.userObject = arcRenderers[4];
        settings.add(userObject);

        userObject = new UserObjectSettings();
		userObject.userObjectColor = Color.black;
		userObject.userObject = arcRenderers[2];
        settings.add(userObject);
        
		userObject = new UserObjectSettings();
		userObject.userObjectColor = new Color(64, 64, 196);
		userObject.userObject = arcRenderers[3];
        settings.add(userObject);
        
        userObject = new UserObjectSettings();
		userObject.userObjectColor = Color.black;
		userObject.userObject = arcRenderers[0];
        settings.add(userObject);
        
        userObject = new UserObjectSettings();
		userObject.userObjectColor = Color.yellow;
		userObject.userObject = arcRenderers[1];
        settings.add(userObject);
        
        userObject = new UserObjectSettings();
        userObject.userObjectColor = new Color(192,192, 0);
        sunAzimuthLineRenderer = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
        sunAzimuthLineRenderer.setLineConnectionPacmanMode(false);
        sunAzimuthLineRenderer.setLineWidth(3.0, LineRenderer.ABSOLUTE_COORDINATES);
        userObject.userObject = sunAzimuthLineRenderer;
        settings.add(userObject);

        return settings;
	}
	
	@Override
	protected void setSunAzimuthLine(Point2D.Double locatePoint1, Point2D.Double locatePoint2, boolean showLine) {
//		System.out.println("Show line? " + showLine);
//		System.out.println(locatePoint1.x + ", " + locatePoint1.y + " TO " + locatePoint2.x + ", " + locatePoint2.y);
		
		
		sunAzimuthLineRenderer.setRenderComponent(showLine);
		if (! showLine)
			return;

		double [] fractionX1 = new double[1];
		double [] fractionY1 = new double[1];
		double [] fractionX2 = new double[1];
		double [] fractionY2 = new double[1];
		
		fractionX1[0] = locatePoint1.x;
		fractionY1[0] = locatePoint1.y;
		fractionX2[0] = locatePoint2.x;
		fractionY2[0] = locatePoint2.y;

		sunAzimuthLineRenderer.setX1Array(fractionX1);
		sunAzimuthLineRenderer.setY1Array(fractionY1);
		sunAzimuthLineRenderer.setX2Array(fractionX2);
		sunAzimuthLineRenderer.setY2Array(fractionY2);
	}


	private TextRenderer createTextRenderer() throws Exception {
		TextRenderer newObject = new TextRenderer(TextRenderer.ABSOLUTE_COORDINATES);
		newObject.setMaintainAspectRatio(false);
		return newObject;
	}
	@Override
	protected ArrayList<TextRenderer> getExtraTextRenderers() throws Exception {
		textRenderers = new TextRenderer[3];
		
		ArrayList<TextRenderer> extraRenderers = new ArrayList<TextRenderer>();
		
		for (int i = 0;i < textRenderers.length;i ++)
			extraRenderers.add(textRenderers[i] = createTextRenderer());
		
		return extraRenderers;
	}
	
	protected List<RendererI> getObjectRenderers() {
		return fullRenderers;
	}
}
