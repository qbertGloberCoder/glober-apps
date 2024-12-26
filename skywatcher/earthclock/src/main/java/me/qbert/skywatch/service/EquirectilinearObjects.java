package me.qbert.skywatch.service;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionRaDec;
import me.qbert.skywatch.service.AbstractCelestialObjects.MapCenterMode;
import me.qbert.skywatch.service.projections.EquirectilinearTransform;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.ui.renderers.DigitalClockImageRenderer;
import me.qbert.skywatch.ui.renderers.EquirectilinearScrollImageRenderer;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.AbstractImageRenderer;
import me.qbert.ui.renderers.LineRenderer;
import me.qbert.ui.renderers.TextRenderer;

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

public class EquirectilinearObjects extends AbstractCelestialObjects {
	private EquirectilinearTransform transform;
	private EquirectilinearScrollImageRenderer equirectinearImageRenderer;
	private DigitalClockImageRenderer clockRenderer;

	public EquirectilinearObjects(Canvas canvas) throws Exception {
		super(canvas);
	}
	
	@Override
	protected AbstractImageRenderer getBackgroundImageRenderer() {
		equirectinearImageRenderer = new EquirectilinearScrollImageRenderer(new File("projections/equirectilinear/map.png"));
		
		return equirectinearImageRenderer;
	}

	@Override
	protected void locationChanged(double latitude, double longitude) {
		equirectinearImageRenderer.wrapToCoordinates(0);
	}
	
	@Override
	protected boolean showObjectsAsPins() {
		return false;
	}
	
	@Override
	protected boolean isLineRenderPacmanMode() {
		return true;
	}
	
	@Override
	protected void postConstructorInit() throws Exception {
		transform = new EquirectilinearTransform();
		init();
	}
	
	@Override
	protected String getProjection() {
		return "equirectilinear";
	}
	
	@Override
	protected RendererI getFillBoundaryRenderer() throws Exception {
		LineRenderer renderer = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
		renderer.setX1(0);
		renderer.setY1(0.11130434782608695652);
		renderer.setX2(1.0);
		renderer.setY2(0.11130434782608695652);
		
		return renderer;
	}
	
	@Override
	protected void setRendererSizeFraction(RendererI renderer, double fraction) {
		((LineRenderer)renderer).setY1(0.11130434782608695652 * fraction);
		((LineRenderer)renderer).setY2(0.11130434782608695652 * fraction);
	}
	
	@Override
	protected RendererI getTopLevelRenderer() {
		File [] digits = new File[10];
		for (int i = 0;i < 10;i ++) {
			digits[i] = new File("clocks/digital/digit" + i + ".png");
		}

		clockRenderer = new DigitalClockImageRenderer(new File("clocks/digital/background.png"),
				digits, new File("clocks/digital/digit_mask.png"), new File("clocks/digital/decorations.png"),
				new File("clocks/digital/decorations_mask.png"));
		clockRenderer.setMaintainAspectRatio(true);
		clockRenderer.setBoundMinimumXFraction(0);
		clockRenderer.setBoundMinimumYFraction(0);
		clockRenderer.setBoundMaximumXFraction(1.0);
		clockRenderer.setBoundMaximumYFraction(0.11130434782608695652);
		
		return clockRenderer;
	}

	
	@Override
	protected double getCircumferenceSizeFraction() {
		return 1.0;
	}
	
	@Override
	protected void setClockLHA(double localHourAngle) {
	    double fracSec = localHourAngle / 360.0 * 24.0;
	    fracSec = fracSec + 0.0000000004999;
	    int hr = (int)(fracSec);
	    fracSec -= (hr);
	    fracSec *= 60.0;
	    int mn = (int)fracSec;
	    fracSec -= (mn);
	    fracSec *= 60;

	    int sc = (int)fracSec;
	    fracSec -= sc;
		
	    
	    Integer fracValue = null;
	    
	    if (getCurrentSpeed() != 0)
	    	fracValue = new Integer((int)(fracSec * 1000.0));
	    
	    while (hr > 24)
	    	hr -= 24;
	    while (hr < 0)
	    	hr += 24;
	    
		clockRenderer.setAspectRatioOverride(equirectinearImageRenderer.getAspectRatio());
	    clockRenderer.setTime(new Integer(hr), new Integer(mn), new Integer(sc), fracValue, isTwentyFourHourClock());
	}
	
	@Override
	protected boolean isShowAnalogClock() {
		return false;
	}
	
	@Override
	public void setShowClock(boolean showClock) {
		clockRenderer.setRenderComponent(showClock);
		super.setShowClock(showClock);
	}
	
	@Override
	protected int getBackgroundRotateDirection() {
		return 0;
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
		return false;
	}

	@Override
	protected boolean allowBrokenContourLines() {
		return false;
	}

	@Override
	public Double updateLocation(double latitude, double longitude) {
		return updateLocation(latitude, longitude, 0);
	}

	@Override
	public Point2D.Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize) {
		return updateLocation(latitude, longitude, 0);
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
		return transform.transform(latitude, longitude, 0.0, getViewRotationAngle(), 0.0);
	}
	
	@Override
	protected boolean isPacmanMode() {
		return true;
	}


	@Override
	protected int getPixelOutOfBoundsXForY(int cartesianYCoordinate, int xBoundary, int yBoundary, double averageRadiusBoundary) {
		if ((Math.abs(cartesianYCoordinate) > yBoundary))
			return 0;
		return xBoundary;
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
		return null;
	}

	@Override
	protected boolean updateUserObject(int userObjectIndex, double latitude, double longitude, double altitude, double diameter) {
		return false;
	}

	@Override
	protected ArrayList<UserObjectSettings> getUserArcRenderObjects() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ArrayList<TextRenderer> getExtraTextRenderers() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
}
