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
import me.qbert.skywatch.service.AbstractCelestialObjects.MapCenterMode;
import me.qbert.skywatch.service.projections.EquirectilinearTransform;
import me.qbert.skywatch.service.projections.GlobeTransform;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.ui.renderers.GlobeImageRenderer;
import me.qbert.skywatch.ui.renderers.RingClockImageRenderer;
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

public class GlobeObjects extends AbstractCelestialObjects {
	private GlobeTransform transform;
	private GlobeImageRenderer globeImageRenderer;
	private RingClockImageRenderer ringClockRenderer;
	private double mapCenterLatitude;
	
	private double circumferenceSizeFraction;
	
	public GlobeObjects(Canvas canvas) throws Exception {
		super(canvas);
	}

	@Override
	protected AbstractImageRenderer getBackgroundImageRenderer() {
		globeImageRenderer = new GlobeImageRenderer(new File("projections/equirectilinear/map.png"));
		
		globeImageRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		
		return globeImageRenderer;
	}
	@Override
	protected void postConstructorInit() throws Exception {
		circumferenceSizeFraction = 0.875;
//		circumferenceSizeFraction = 0.975;
		transform = new GlobeTransform();
		transform.setZoomLevel(circumferenceSizeFraction);
		init();
	}

	@Override
	public void setFullSize(boolean fullSize) {
		double newSize;
		
		if (fullSize)
			newSize = 0.975;
		else
			newSize = 0.875;
		
		if (newSize != circumferenceSizeFraction) {
			circumferenceSizeFraction = newSize;
			reevaluateComponentSizes();
		}
		
		
	}
	
	@Override
	protected void reevaluateComponentSizes() {
		transform.setZoomLevel(circumferenceSizeFraction);
		globeImageRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		ringClockRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		
		super.reevaluateComponentSizes();
	}
	
	@Override
	public boolean isFullSize() {
		return (circumferenceSizeFraction >= 0.95) ? true : false;
	}
	
	
	@Override
	protected RendererI getTopLevelRenderer() {
		ringClockRenderer = new RingClockImageRenderer(new File("clocks/ringclock/numbers.png"));
//		ringClockRenderer.setMaintainAspectRatio(false);
//		ringClockRenderer.setRenderComponent(false);
		ringClockRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		return ringClockRenderer;
	}

	
	@Override
	protected void locationChanged(double latitude, double longitude) {
		mapCenterLatitude = (getCenterLocation() !=  MapCenterMode.OBSERVER_LON) ? latitude : 0.0;
		globeImageRenderer.wrapToCoordinates(mapCenterLatitude, longitude);
	}
	
	@Override
	protected boolean showObjectsAsPins() {
		if (circumferenceSizeFraction < 0.95)
			return true;
		
		return false;
	}
	
	@Override
	protected boolean isLineRenderPacmanMode() {
		return false;
	}
	
	@Override
	protected String getProjection() {
		return "globe";
	}
	
	@Override
	protected boolean isDrawCircumference() {
		return true;
	}
	
	@Override
	protected double getCircumferenceSizeFraction() {
		return circumferenceSizeFraction;
	}
	
	@Override
	protected boolean isShowAnalogClock() {
		return false;
	}
	
	@Override
	protected void setClockLHA(double localHourAngle) {
		ringClockRenderer.wrapToLHA(localHourAngle);
	}
	
	@Override
	protected int getBackgroundRotateDirection() {
		return 0;
	}

	@Override
	protected boolean allowBrokenContourLines() {
		return true;
	}

	@Override
	protected double [] getFillBearings() {
		double [] bearings = {90.0, 180.0, 0.0, 270.0, 
				45.0, 135.0, 225.0, 315.0, 
				30.0, 60.0, 120.0, 150.0, 210.0, 240.0, 300.0, 330.0,
				15.0, 75.0, 105.0, 165.0, 195.0, 255.0, 285.0, 345.0};
		return bearings;
	}

	@Override
	protected double getFillStartBearing() {
		return -90.0;
	}
	
	@Override
	protected boolean isDynamicContourLineBearings() {
		return true;
	}

	@Override
	protected Double updateLocation(double latitude, double longitude) {
		return updateLocation(latitude, longitude, 0);
	}

	@Override
	protected Point2D.Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize) {
		return updateLocation(latitude, longitude, 0, renderFullCircumferenceSize);
	}
	
	@Override
	protected Double updateLocation(double latitude, double longitude, double observerLongitude) {
		return updateLocation(latitude, longitude, observerLongitude, false);
	}
	
	private Double updateLocation(double latitude, double longitude, double observerLongitude, boolean renderFullCircumferenceSize) {
		if ((renderFullCircumferenceSize) && (circumferenceSizeFraction < 0.95))
			transform.setZoomedOut(false);
		Double rval = transform.transform(latitude, longitude, mapCenterLatitude, getViewRotationAngle(), observerLongitude);
		if (renderFullCircumferenceSize)
			transform.setZoomedOut(true);
		return rval;
	}
	@Override
	protected boolean isPacmanMode() {
		return false;
	}
	
	@Override
	public void setShowClock(boolean showClock) {
		ringClockRenderer.setRenderComponent(showClock);
		super.setShowClock(showClock);
	}
}
