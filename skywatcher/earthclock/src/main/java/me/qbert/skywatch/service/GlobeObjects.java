package me.qbert.skywatch.service;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.io.File;
import me.qbert.skywatch.service.projections.GlobeTransform;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.ui.renderers.GlobeImageRenderer;
import me.qbert.skywatch.ui.renderers.RingClockImageRenderer;
import me.qbert.skywatch.ui.renderers.StarClockImageRenderer;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.AbstractImageRenderer;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.BoundaryContainerRenderer;

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
	private RingClockImageRenderer slipRingClockRenderer;
	private StarClockImageRenderer starRingClockRenderer;
	private double mapCenterLatitude;
	
	private RendererI ringClockRenderer;
	
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
		
		if (ringClockRenderer.getClass().isInstance(slipRingClockRenderer))
			slipRingClockRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		if (ringClockRenderer.getClass().isInstance(starRingClockRenderer))
			starRingClockRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		
		
		super.reevaluateComponentSizes();
	}
	
	@Override
	public boolean isFullSize() {
		return (circumferenceSizeFraction >= 0.95) ? true : false;
	}
	

	private RendererI getRingClockRenderer() {
		if (slipRingClockRenderer == null) {
			slipRingClockRenderer = new RingClockImageRenderer(new File("clocks/ringclock/numbers.png"));
			slipRingClockRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		}
		ringClockRenderer = slipRingClockRenderer;
		return ringClockRenderer;
	}
	
	private RendererI getStarClockRenderer() {
		if (starRingClockRenderer == null) {
			starRingClockRenderer = new StarClockImageRenderer(this);
			starRingClockRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		}
		ringClockRenderer = starRingClockRenderer;
		return ringClockRenderer;
	}
	
	@Override
	protected RendererI getTopLevelRenderer() {
		return getStarClockRenderer();
	}

	public void changeTopLevelRenderer(int topRendererIndex) {
		if (topRendererIndex == 0) {
			changeTopLevelRenderer(getStarClockRenderer());
		}
		if (topRendererIndex == 1) {
			changeTopLevelRenderer(getRingClockRenderer());
		}
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
		return circumferenceSizeFraction;
	}
	
	@Override
	protected boolean isShowAnalogClock() {
		return false;
	}
	
	@Override
	protected void setClockLHA(double localHourAngle) {
		if (ringClockRenderer.getClass().isInstance(slipRingClockRenderer))
			slipRingClockRenderer.wrapToLHA(localHourAngle);
		if (ringClockRenderer.getClass().isInstance(starRingClockRenderer))
			starRingClockRenderer.wrapToLHA(localHourAngle);
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
	public Double updateLocation(double latitude, double longitude) {
		return updateLocation(latitude, longitude, 0);
	}

	@Override
	public Point2D.Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize) {
		return updateLocation(latitude, longitude, 0, renderFullCircumferenceSize);
	}
	
	@Override
	public Double updateLocation(double latitude, double longitude, double observerLongitude) {
		return updateLocation(latitude, longitude, observerLongitude, false);
	}
	
	@Override
	public Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize, double overscan) {
		return updateLocation(latitude, longitude, -getViewRotationAngle(), renderFullCircumferenceSize, overscan);
	}
	
	private Double updateLocation(double latitude, double longitude, double observerLongitude, boolean renderFullCircumferenceSize) {
		return updateLocation(latitude, longitude, observerLongitude, renderFullCircumferenceSize, 1.0);
	}
	
	public Double updateLocation(double latitude, double longitude, double observerLongitude, boolean renderFullCircumferenceSize, double overscan) {
		if ((renderFullCircumferenceSize) && (circumferenceSizeFraction < 0.95))
			transform.setZoomedOut(false);
		Double rval = transform.transform(latitude, longitude, mapCenterLatitude, getViewRotationAngle(), observerLongitude, overscan);
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
		if (showClock) {
			if (ringClockRenderer.getClass().isInstance(slipRingClockRenderer))
				changeTopLevelRenderer(0);
			else if (ringClockRenderer.getClass().isInstance(starRingClockRenderer))
				changeTopLevelRenderer(1);
		}
		if (ringClockRenderer.getClass().isInstance(slipRingClockRenderer))
			slipRingClockRenderer.setRenderComponent(showClock);
		if (ringClockRenderer.getClass().isInstance(starRingClockRenderer))
			starRingClockRenderer.setRenderComponent(showClock);
		super.setShowClock(showClock);
	}

	@Override
	protected int getPixelOutOfBoundsXForY(int cartesianYCoordinate, int xBoundary, int yBoundary, double averageRadiusBoundary) {
		return (int)Math.sqrt(averageRadiusBoundary*averageRadiusBoundary-cartesianYCoordinate*cartesianYCoordinate);
	}
}
