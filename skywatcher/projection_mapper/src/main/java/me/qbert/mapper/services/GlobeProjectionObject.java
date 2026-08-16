package me.qbert.mapper.services;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.AbstractImageRenderer;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.mapper.ui.components.Canvas;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.mapper.renderers.PinnableCelestialObject;
import me.qbert.mapper.services.projections.GlobeTransform;
import me.qbert.mapper.model.AnimationConfiguration;
import me.qbert.mapper.renderers.GlobeImageRenderer;
import me.qbert.ui.renderers.LineRenderer;

public class GlobeProjectionObject extends AbstractProjectionObject {
	public GlobeProjectionObject(Canvas canvas, AnimationConfiguration animationConfiguration) throws Exception {
		super(canvas, animationConfiguration);
	}

	private double circumferenceSizeFraction;
	private GlobeTransform transform;
	
	private double latOff = -1;
	private double lonOff = 1.0;

	@Override
	protected AbstractImageRenderer getBackgroundImageRenderer() {
		GlobeImageRenderer globeImageRenderer = new GlobeImageRenderer(new File("projections/equirectilinear/map.png"));
		
		transform.setZoomLevel(circumferenceSizeFraction);
		globeImageRenderer.setCircumferenceSizeFraction(circumferenceSizeFraction);
		globeImageRenderer.wrapToCoordinates(getObserverLocation().getLatitude()+latOff, getObserverLocation().getLongitude()+lonOff);
		
		return globeImageRenderer;
	}
	
	@Override
	protected void init() {
		transform = new GlobeTransform();
		transform.setZoomLevel(circumferenceSizeFraction);
		
		circumferenceSizeFraction = 0.875;
	}

	@Override
	protected String getProjection() {
		return "globe";
	}
	
	protected void adjustPin(PinnableCelestialObject object, int size) {
		object.setInnerPinSize(size);
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
		return updateLocation(latitude, longitude, 0, renderFullCircumferenceSize, overscan);
	}
	
	private Double updateLocation(double latitude, double longitude, double observerLongitude, boolean renderFullCircumferenceSize) {
		return updateLocation(latitude, longitude, observerLongitude, renderFullCircumferenceSize, 1.0);
	}

	public Double updateLocation(double latitude, double longitude, double observerLongitude, boolean renderFullCircumferenceSize, double overscan) {
		return updateLocation(latitude, longitude, observerLongitude, renderFullCircumferenceSize, overscan, false);
	}
	
	public Double updateLocation(double latitude, double longitude, double observerLongitude, boolean renderFullCircumferenceSize, double overscan, boolean positiveZOnly) {
		if ((renderFullCircumferenceSize) && (circumferenceSizeFraction < 0.95))
			transform.setZoomedOut(false);
		Double rval = transform.transform(latitude, longitude, getObserverLocation().getLatitude(), getObserverLocation().getLongitude(), observerLongitude, overscan, positiveZOnly);
		if (renderFullCircumferenceSize)
			transform.setZoomedOut(true);
		return rval;
	}
}
