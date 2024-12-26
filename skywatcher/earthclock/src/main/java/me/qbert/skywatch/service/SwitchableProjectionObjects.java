package me.qbert.skywatch.service;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
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
import me.qbert.skywatch.service.projections.EquirectilinearTransform;
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

public class SwitchableProjectionObjects extends AbstractCelestialObjects {
	public enum ProjectionType {
		AE("Azimuthal Equidstant (NP)"),
		EQUIRECTILINEAR("Equirectininear");
		
	    private String name;

	    private ProjectionType(String stringVal) {
	        name=stringVal;
	    }
	    public String toString(){
	        return name;
	    }

	    public static String getEnumByString(String code){
	        for(ProjectionType e : ProjectionType.values()){
	            if(e.name.equals(code)) return e.name();
	        }
	        return null;
	    }
	}
	
	public static final ProjectionType [] PROJECTION_TYPES = {ProjectionType.AE, ProjectionType.EQUIRECTILINEAR};
	
	private HashMap<ProjectionType, AbstractCelestialObjects> dispatcher;
	private AbstractCelestialObjects activeProjection = null;
	
	public SwitchableProjectionObjects(Canvas canvas) throws Exception {
		super(canvas);
	}
	
	@Override
	protected boolean isLineRenderPacmanMode() {
		return activeProjection.isLineRenderPacmanMode();
	}
	
	protected void postConstructorInit() throws Exception {
		dispatcher = new HashMap<ProjectionType, AbstractCelestialObjects>();
		
		try {
			AbstractCelestialObjects tmpProjection = new AzimuthalEquidistantNPPObjects(getCanvas());
			dispatcher.put(ProjectionType.AE, tmpProjection);
			activeProjection = tmpProjection;
			
			tmpProjection = new EquirectilinearObjects(getCanvas());
			dispatcher.put(ProjectionType.EQUIRECTILINEAR, tmpProjection);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		init();
	}
	
	public void setProjectionMode(ProjectionType projectionType) {
		AbstractCelestialObjects tmpProjection = dispatcher.get(projectionType);
		if (tmpProjection != null)
			activeProjection = tmpProjection;
	}
	
	@Override
	protected boolean showObjectsAsPins() {
		return activeProjection.showObjectsAsPins();
	}
	
	@Override
	protected void locationChanged(double latitude, double longitude) {
	}
	
	@Override
	protected void setClockLHA(double localHourAngle) {
		activeProjection.setClockLHA(localHourAngle);
	}
	
	@Override
	protected String getProjection() {
		return activeProjection.getProjection();
	}
	
	@Override
	protected RendererI getFillBoundaryRenderer() throws Exception {
		return activeProjection.getFillBoundaryRenderer();
	}
	
	@Override
	protected void setRendererSizeFraction(RendererI renderer, double fraction) {
		activeProjection.setRendererSizeFraction(renderer, fraction);
	}
	
	@Override
	protected double getCircumferenceSizeFraction() {
		return activeProjection.getCircumferenceSizeFraction();
	}
	
	@Override
	protected boolean isShowAnalogClock() {
		return activeProjection.isShowAnalogClock();
	}
	
	@Override
	protected int getBackgroundRotateDirection() {
		return activeProjection.getBackgroundRotateDirection();
	}
	
	@Override
	protected boolean allowBrokenContourLines() {
		return activeProjection.allowBrokenContourLines();
	}

	@Override
	protected double [] getFillBearings() {
		return activeProjection.getFillBearings();
	}
	
	@Override
	protected double getFillStartBearing() {
		return activeProjection.getFillStartBearing();
	}
	
	@Override
	protected boolean isDynamicContourLineBearings() {
		return activeProjection.isDynamicContourLineBearings();
	}

	@Override
	public Double updateLocation(double latitude, double longitude) {
		return activeProjection.updateLocation(latitude, longitude);
	}

	@Override
	public Double updateLocation(double latitude, double longitude, double observerLongitude) {
		return activeProjection.updateLocation(latitude, longitude, observerLongitude);
	}
	
	@Override
	public Point2D.Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize) {
		return activeProjection.updateLocation(latitude, longitude, renderFullCircumferenceSize);
	}
	
	@Override
	public Point2D.Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize, double overscan) {
		return activeProjection.updateLocation(latitude, longitude, renderFullCircumferenceSize, overscan);
	}
	
	@Override
	protected boolean isPacmanMode() {
		return activeProjection.isPacmanMode();
	}


	@Override
	protected int getPixelOutOfBoundsXForY(int cartesianYCoordinate, int xBoundary, int yBoundary, double averageRadiusBoundary) {
		return activeProjection.getPixelOutOfBoundsXForY(cartesianYCoordinate, xBoundary, yBoundary, averageRadiusBoundary);
	}

	@Override
	protected void setRenderers(List<RendererI> renderers) {
		getCanvas().setRenderers(renderers);
	}

	@Override
	protected void repaintRequest() {
		getCanvas().repaint();
	}

	@Override
	protected Point2D.Double getMoonShadowCoordinate(MapCenterMode centerMode, GeoLocation subSunPoint, GeoLocation subMoonPoint) {
		return activeProjection.getMoonShadowCoordinate(centerMode, subSunPoint, subMoonPoint);
	}

	@Override
	protected boolean updateUserObject(int userObjectIndex, double latitude, double longitude, double altitude, double diameter) {
		return activeProjection.updateUserObject(userObjectIndex, latitude, longitude, altitude, diameter);
	}

	@Override
	protected ArrayList<UserObjectSettings> getUserArcRenderObjects() throws Exception {
		return activeProjection.getUserArcRenderObjects();
	}

	@Override
	protected ArrayList<TextRenderer> getExtraTextRenderers() throws Exception {
		return activeProjection.getExtraTextRenderers();
	}
}
