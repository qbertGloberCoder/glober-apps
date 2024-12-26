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
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.ui.renderers.GlobeImageRenderer;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.SplitContainerRenderer;
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

public class StereoGlobeObjects extends AbstractCelestialObjects {
	private GlobeObjects leftPanel;
	private GlobeObjects rightPanel;
	
	private SplitContainerRenderer splitContainer;
	private double stereoAngle = 1.5;
	
	public StereoGlobeObjects(Canvas canvas) throws Exception {
		super(canvas);
	}

	@Override
	public Double updateLocation(double latitude, double longitude) {
		leftPanel.updateLocation(latitude, longitude);
		return rightPanel.updateLocation(latitude, longitude);
	}

	@Override
	public Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize) {
		leftPanel.updateLocation(latitude, longitude, renderFullCircumferenceSize);
		return rightPanel.updateLocation(latitude, longitude, renderFullCircumferenceSize);
	}

	@Override
	public Double updateLocation(double latitude, double longitude, double observerLongitude) {
		leftPanel.updateLocation(latitude, longitude, observerLongitude);
		return rightPanel.updateLocation(latitude, longitude, observerLongitude);
	}

	@Override
	public Double updateLocation(double latitude, double longitude, boolean renderFullCircumferenceSize,
			double overscan) {
		leftPanel.updateLocation(latitude, longitude, renderFullCircumferenceSize, overscan);
		return rightPanel.updateLocation(latitude, longitude, renderFullCircumferenceSize, overscan);
	}
	
	public double getStereoAngle() {
		return stereoAngle;
	}

	public void setStereoAngle(double stereoAngle) {
		this.stereoAngle = stereoAngle;
		
		updateStereoAngle();
	}
	
	private void updateStereoAngle() {
		this.leftPanel.setStereoVisionRotate(-stereoAngle);
		this.rightPanel.setStereoVisionRotate(stereoAngle);
	}

	@Override
	protected void postConstructorInit() throws Exception {
		this.leftPanel = new GlobeObjects(getCanvas());
		this.rightPanel = new GlobeObjects(getCanvas());
		
		updateStereoAngle();
		
		init();
		
		List<RendererI> localRenderers = new ArrayList<RendererI>();
		
		splitContainer = new SplitContainerRenderer(SplitContainerRenderer.A_LEFT_OF_B, 0.5);
		
    	// RIGHT side
		List<RendererI> panelRenderers = rightPanel.getObjectRenderers();
		//System.out.println("?? PANEL renderers??? " + panelRenderers);
    	splitContainer.setRendererB(panelRenderers);
    	
    	// LEFT side
		panelRenderers = leftPanel.getObjectRenderers();
		//System.out.println("?? PANEL renderers??? " + panelRenderers);
    	splitContainer.setRendererA(panelRenderers);
    	
    	localRenderers = new ArrayList<RendererI>();

    	localRenderers.add(splitContainer);
    	
    	getCanvas().setRenderers(localRenderers);
    	
    	splitContainer.setRenderA(true);
    	splitContainer.setRenderB(true);
    	
		//System.out.println("?? SET Canvas renderers??? " + localRenderers);

//		leftPanel.updateSequenceGenerator(getSequenceGenerator());
//		rightPanel.updateSequenceGenerator(getSequenceGenerator());
	}

	@Override
	protected void setRenderers(List<RendererI> renderers) {
	}

	@Override
	protected void setRendererSizeFraction(RendererI renderer, double fraction) {
		leftPanel.setRendererSizeFraction(renderer, fraction);
		rightPanel.setRendererSizeFraction(renderer, fraction);
	}

	@Override
	protected RendererI getFillBoundaryRenderer() throws Exception {
		return splitContainer;
	}

	@Override
	protected boolean isLineRenderPacmanMode() {
		return false;
	}

	@Override
	protected double getCircumferenceSizeFraction() {
		return 0;
	}

	@Override
	protected String getProjection() {
		return rightPanel.getProjection();
	}

	@Override
	protected boolean isShowAnalogClock() {
		return rightPanel.isShowAnalogClock();
	}

	@Override
	protected void setClockLHA(double localHourAngle) {
		leftPanel.setClockLHA(localHourAngle);
		rightPanel.setClockLHA(localHourAngle);
	}

	@Override
	protected boolean showObjectsAsPins() {
		return rightPanel.showObjectsAsPins();
	}

	@Override
	protected void repaintRequest() {
		getCanvas().repaint();
	}

	@Override
	protected boolean allowBrokenContourLines() {
		return rightPanel.allowBrokenContourLines();
	}

	@Override
	protected int getBackgroundRotateDirection() {
		return rightPanel.getBackgroundRotateDirection();
	}

	@Override
	protected boolean isDynamicContourLineBearings() {
		return rightPanel.isDynamicContourLineBearings();
	}

	@Override
	protected double[] getFillBearings() {
		return rightPanel.getFillBearings();
	}

	@Override
	protected double getFillStartBearing() {
		return rightPanel.getFillStartBearing();
	}

	@Override
	protected void locationChanged(double latitude, double longitude) {
		leftPanel.locationChanged(latitude, longitude);
		rightPanel.locationChanged(latitude, longitude);
	}

	@Override
	protected boolean isPacmanMode() {
		return rightPanel.isPacmanMode();
	}

	@Override
	protected int getPixelOutOfBoundsXForY(int cartesianYCoordinate, int xBoundary, int yBoundary,
			double averageRadiusBoundary) {
		return rightPanel.getPixelOutOfBoundsXForY(cartesianYCoordinate, xBoundary, yBoundary, averageRadiusBoundary);
	}
	
	@Override
	public void runScript() {
		leftPanel.runScript();
		rightPanel.runScript();
	}

	@Override
	public void abortScript() {
		leftPanel.abortScript();
		rightPanel.abortScript();
	}
	
	@Override
	public boolean isRunningScript() {
		return rightPanel.isRunningScript();
	}
	
	@Override
	public void setTimeBias(int timeBiasSeconds) {
		leftPanel.setTimeBias(timeBiasSeconds);
		rightPanel.setTimeBias(timeBiasSeconds);
	}


	@Override
	public void updateObjects(boolean repaintCanavas) throws Exception {
//		boolean dstChanged = getSequenceGenerator().advanceSequence(getDstRotateSetting() == 2);
		
		leftPanel.updateObjects(false); //, getSequenceGenerator(), dstChanged);
		rightPanel.updateObjects(false); //, getSequenceGenerator(), dstChanged);
		
        if (repaintCanavas)
        	repaintRequest();
	}
	
	@Override
	public void setShowClock(boolean showClock) {
		leftPanel.setShowClock(showClock);
		rightPanel.setShowClock(showClock);
	}
	
	@Override
	public void setSunRenderContourLines(boolean contourLines) {
		leftPanel.setSunRenderContourLines(contourLines);
		rightPanel.setSunRenderContourLines(contourLines);
	}

	@Override
	public void setMoonRenderContourLines(boolean contourLines) {
		leftPanel.setMoonRenderContourLines(contourLines);
		rightPanel.setMoonRenderContourLines(contourLines);
	}
	
	@Override
	public void setDayNightRendered(boolean rendered) {
		leftPanel.setDayNightRendered(rendered);
		rightPanel.setDayNightRendered(rendered);
	}
	
	@Override
	public void setDayNightFillLevel(int fillLevel) {
		leftPanel.setDayNightFillLevel(fillLevel);
		rightPanel.setDayNightFillLevel(fillLevel);
	}
	
	@Override
	public boolean isDayNightRendered() {
		return rightPanel.isDayNightRendered();
	}

	@Override
	public void setMoonDayNightRendered(boolean rendered) {
		leftPanel.setMoonDayNightRendered(rendered);
		rightPanel.setMoonDayNightRendered(rendered);
	}
	
	@Override
	public boolean isMoonDayNightRendered() {
		return rightPanel.isMoonDayNightRendered();
	}
	
	@Override
	public void setClockFace(AbstractCelestialObjects.ClockFaces clockFace) {
		leftPanel.setClockFace(clockFace);
		rightPanel.setClockFace(clockFace);
	}
	
	@Override
	public void setSunPrecessionPathMode(int mode)  {
		leftPanel.setSunPrecessionPathMode(mode);
		rightPanel.setSunPrecessionPathMode(mode);
	}
	
	@Override
	public void setMoonPrecessionPathMode(int mode)  {
		leftPanel.setMoonPrecessionPathMode(mode);
		rightPanel.setMoonPrecessionPathMode(mode);
	}
	
	@Override
	public void setShiftDirection(int shiftDirection) {
		leftPanel.setShiftDirection(shiftDirection);
		rightPanel.setShiftDirection(shiftDirection);
	}
	
	@Override
	public int getSunPrecessionPathMode() {
		return rightPanel.getSunPrecessionPathMode();
	}
	
	@Override
	public int getMoonPrecessionPathMode() {
		return rightPanel.getMoonPrecessionPathMode();
	}
	
	@Override
	public void setPlanetsVisible(boolean visible) {
		leftPanel.setPlanetsVisible(visible);
		rightPanel.setPlanetsVisible(visible);
	}

	@Override
	public void setDstRotateSetting(int dstRotateSetting) {
		leftPanel.setDstRotateSetting(dstRotateSetting);
		rightPanel.setDstRotateSetting(dstRotateSetting);
	}
	
	@Override
	public void setCenterLocation(MapCenterMode centerMode) {
		leftPanel.setCenterLocation(centerMode);
		rightPanel.setCenterLocation(centerMode);
	}
	
	@Override
	public int getDstRotateSetting() {
		return rightPanel.getDstRotateSetting();
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
		leftPanel.updateLocation(arc, latitude, longitude, size, renderFullCircumferenceSize);
		rightPanel.updateLocation(arc, latitude, longitude, size, renderFullCircumferenceSize);
	}
	
	@Override
	public void speedDown() {
		leftPanel.speedDown();
		rightPanel.speedDown();
	}
	
	@Override
	public void speedUp() {
		leftPanel.speedUp();
		rightPanel.speedUp();
	}
	
	@Override
	public void setSpeed(int speedIndex) {
		leftPanel.setSpeed(speedIndex);
		rightPanel.setSpeed(speedIndex);
	}
	
	@Override
	public int getCurrentSpeed() {
		return rightPanel.getCurrentSpeed();
	}
	
	@Override
	public void resetDayNightRender() {
		leftPanel.resetDayNightRender();
		rightPanel.resetDayNightRender();
	}
	
	@Override
	public int animateSpeedHint() {
		return rightPanel.animateSpeedHint();
	}

	@Override
	public void setLatitude(double newLatitude) {
		leftPanel.setLatitude(newLatitude);
		rightPanel.setLatitude(newLatitude);
	}
	
	@Override
	public void setLongitude(double newLongitude) {
		leftPanel.setLongitude(newLongitude);
		rightPanel.setLongitude(newLongitude);
	}
	
	@Override
	public void setRenderSunGPGreatCircleRoute(boolean renderSunGPGreatCircleRoute) {
		leftPanel.setRenderSunGPGreatCircleRoute(renderSunGPGreatCircleRoute);
		rightPanel.setRenderSunGPGreatCircleRoute(renderSunGPGreatCircleRoute);
	}
	
	@Override
	public void setRenderMoonGPGreatCircleRoute(boolean renderMoonGPGreatCircleRoute) {
		leftPanel.setRenderMoonGPGreatCircleRoute(renderMoonGPGreatCircleRoute);
		rightPanel.setRenderMoonGPGreatCircleRoute(renderMoonGPGreatCircleRoute);
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
