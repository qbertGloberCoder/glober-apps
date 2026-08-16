package me.qbert.mapper.services;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.io.File;

import me.qbert.mapper.model.AnimationConfiguration;
import me.qbert.mapper.renderers.MercatorScrollImageRenderer;
import me.qbert.mapper.renderers.PinnableCelestialObject;
import me.qbert.mapper.services.projections.MercatorTransform;
import me.qbert.mapper.ui.components.Canvas;
import me.qbert.ui.renderers.AbstractImageRenderer;

public class MercatorObjects extends AbstractProjectionObject {
	private MercatorScrollImageRenderer mercatorImageRenderer;
	
	public MercatorObjects(Canvas canvas, AnimationConfiguration animationConfiguration) throws Exception {
		super(canvas, animationConfiguration);
	}

	private MercatorTransform transform;
	
	@Override
	protected void init() {
		transform = new MercatorTransform();
	}

	@Override
	protected AbstractImageRenderer getBackgroundImageRenderer() {
		mercatorImageRenderer = new MercatorScrollImageRenderer(new File("projections/mercator/map.jpg"));
		
		mercatorImageRenderer.setRenderComponent(true);
		mercatorImageRenderer.wrapToCoordinates(getObserverLocation().getLongitude());
		
		return mercatorImageRenderer;
	}

	@Override
	protected String getProjection() {
		return "mercator";
	}
	
	protected void adjustPin(PinnableCelestialObject object, int size) {
		object.setOuterPinSize(size);
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
		return transform.transform(latitude, longitude, 0, observerLongitude, 0.0);
	}
	
	public Double updateLocation(double latitude, double longitude, double observerLongitude, double overscan) {
		return transform.transform(latitude, longitude, 0.0, 0.0, 0.0);
	}
}
