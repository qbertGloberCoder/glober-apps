package me.qbert.mapper.services;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import me.qbert.mapper.ui.components.Canvas;
import me.qbert.mapper.model.AnimationConfiguration;
import me.qbert.mapper.renderers.PinnableCelestialObject;
import me.qbert.mapper.services.projections.AzimuthalEquidistantNPTransform;

public class AzimuthalEquidistantNPPObjects extends AbstractProjectionObject {
	public AzimuthalEquidistantNPPObjects(Canvas canvas, AnimationConfiguration animationConfiguration) throws Exception {
		super(canvas, animationConfiguration);
	}

	private AzimuthalEquidistantNPTransform transform;
	
	@Override
	protected void init() {
		transform = new AzimuthalEquidistantNPTransform();
	}

	@Override
	protected String getProjection() {
		return "ae-north";
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
		return transform.transform(latitude, longitude, 0, observerLongitude, 0);
	}
	
	public Double updateLocation(double latitude, double longitude, double observerLongitude, double overscan) {
		return transform.transform(latitude, longitude, 0, observerLongitude, 0, overscan);
	}
}
