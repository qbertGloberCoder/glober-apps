package me.qbert.skywatch.service;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import me.qbert.skywatch.service.projections.AzimuthalEquidistantSPTransform;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ArcRenderer;

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

public class AzimuthalEquidistantSPPObjects extends AbstractCelestialObjects {
	private AzimuthalEquidistantSPTransform transform;

	public AzimuthalEquidistantSPPObjects(Canvas canvas) throws Exception {
		super(canvas);
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
		transform = new AzimuthalEquidistantSPTransform();
		init();
	}
	
	@Override
	protected String getProjection() {
		return "ae-south";
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
	protected int getBackgroundRotateDirection() {
		return -1;
	}

	@Override
	protected boolean allowBrokenContourLines() {
		return false;
	}

	@Override
	protected void setClockLHA(double localHourAngle) {
	}
	
	@Override
	protected boolean isShowAnalogClock() {
		return true;
	}
	
	@Override
	protected double [] getFillBearings() {
		double [] bearings = {180.0};
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
		return updateLocation(latitude, longitude, -getViewRotationAngle());
	}

	@Override
	public Point2D.Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize) {
		return updateLocation(latitude, longitude, -getViewRotationAngle());
	}
	
	@Override
	public Double updateLocation(double latitude, double longitude, double observerLongitude) {
		return transform.transform(latitude, longitude, 0, observerLongitude, (getDstRotate() * 15.0));
	}
	
	@Override
	protected boolean isPacmanMode() {
		return false;
	}

	@Override
	protected boolean isPixelOutOfBounds(int cartesianXCoordinate, int cartesianYCoordinate, int xBoundary, int yBoundary, double averageRadiusBoundary) {
		double radius = Math.sqrt(cartesianXCoordinate*cartesianXCoordinate+cartesianYCoordinate*cartesianYCoordinate);
		if (radius > averageRadiusBoundary)
			return true;
		return false;
	}
}
