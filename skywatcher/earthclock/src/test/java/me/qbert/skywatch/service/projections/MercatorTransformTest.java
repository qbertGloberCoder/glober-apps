package me.qbert.skywatch.service.projections;

import java.awt.geom.Point2D.Double;

public class MercatorTransformTest {

	public static void main(String[] args) {
		MercatorTransform mercator = new MercatorTransform();
		
		Double point = mercator.transform(90, 0.0, 0.0, 0.0, 0.0, 0.0);
		System.out.println("POINT??? " + point.x + ", " + point.y);
	}

}
