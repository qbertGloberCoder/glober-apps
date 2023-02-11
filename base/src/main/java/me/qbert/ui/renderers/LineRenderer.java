package me.qbert.ui.renderers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
import java.awt.Stroke;
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
import java.awt.Stroke;
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
import java.awt.Stroke;
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
import java.util.ArrayList;

import me.qbert.ui.coordinates.AbsoluteCoordinateTransformation;
import me.qbert.ui.coordinates.AbstractCoordinateTransformation;
import me.qbert.ui.coordinates.FractionCoordinateTransformation;

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

public class LineRenderer extends AbstractFractionRenderer {
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
	private ArrayList<AbstractCoordinateTransformation[]> lineSegments;
	private Double lineWidth = null;
	private int lineWidthTransformType;
	
	private double alphaChannel = 1.0;
<<<<<<< HEAD
<<<<<<< HEAD
	
	private int coordinatesType;
	
	private int lineConnectionMode = 1;
	
<<<<<<< HEAD
<<<<<<< HEAD
	public LineRenderer(int coordinatesType) throws Exception {
		if ((coordinatesType == ABSOLUTE_COORDINATES) || (coordinatesType == FRACTIONAL_COORDINATES)) {
			this.coordinatesType = coordinatesType;
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
		
		initializeList(1);
	}
=======
	ArrayList<AbstractCoordinateTransformation[]> lineSegments;
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
	
	private int coordinatesType;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
	
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
	public LineRenderer(int coordinatesType) throws Exception {
		if ((coordinatesType == ABSOLUTE_COORDINATES) || (coordinatesType == FRACTIONAL_COORDINATES)) {
			this.coordinatesType = coordinatesType;
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
		
		initializeList(1);
	}
=======
	ArrayList<AbstractCoordinateTransformation[]> lineSegments;
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
	
	private int coordinatesType;
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
	
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
	public LineRenderer(int coordinatesType) throws Exception {
		if ((coordinatesType == ABSOLUTE_COORDINATES) || (coordinatesType == FRACTIONAL_COORDINATES)) {
			this.coordinatesType = coordinatesType;
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
		
		initializeList(1);
	}
=======
	ArrayList<AbstractCoordinateTransformation[]> lineSegments;
	
	private int coordinatesType;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
	
	public LineRenderer(int coordinatesType) throws Exception {
		if ((coordinatesType == ABSOLUTE_COORDINATES) || (coordinatesType == FRACTIONAL_COORDINATES)) {
			this.coordinatesType = coordinatesType;
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
		
		initializeList(1);
	}
	
	private void initializeList(int count) {
		lineSegments = new ArrayList<AbstractCoordinateTransformation[]>();
		for (int i = 0;i < count;i ++) {
			AbstractCoordinateTransformation [] linePair = new AbstractCoordinateTransformation[2];
			
			if (coordinatesType == ABSOLUTE_COORDINATES) {
				linePair[0] = new AbsoluteCoordinateTransformation();
				linePair[1] = new AbsoluteCoordinateTransformation();
			} else if (coordinatesType == FRACTIONAL_COORDINATES) {
				linePair[0] = new FractionCoordinateTransformation();
				linePair[1] = new FractionCoordinateTransformation();
			}
			
			lineSegments.add(linePair);
		}
	}

	@Override
	public double getAspectRatio() {
		return -1.0;
	}
	
	public void setLineConnectionPacmanMode(boolean pacmanMode) {
		if (pacmanMode)
			lineConnectionMode = 2;
		else
			lineConnectionMode = 1;
	}
	
	private void initializeList(int count) {
		lineSegments = new ArrayList<AbstractCoordinateTransformation[]>();
		for (int i = 0;i < count;i ++) {
			AbstractCoordinateTransformation [] linePair = new AbstractCoordinateTransformation[2];
			
			if (coordinatesType == ABSOLUTE_COORDINATES) {
				linePair[0] = new AbsoluteCoordinateTransformation();
				linePair[1] = new AbsoluteCoordinateTransformation();
			} else if (coordinatesType == FRACTIONAL_COORDINATES) {
				linePair[0] = new FractionCoordinateTransformation();
				linePair[1] = new FractionCoordinateTransformation();
			}
			
			lineSegments.add(linePair);
		}
	}

	@Override
	public double getAspectRatio() {
		return -1.0;
	}
	
	public void setLineConnectionPacmanMode(boolean pacmanMode) {
		if (pacmanMode)
			lineConnectionMode = 2;
		else
			lineConnectionMode = 1;
	}
	
	private void initializeList(int count) {
		lineSegments = new ArrayList<AbstractCoordinateTransformation[]>();
		for (int i = 0;i < count;i ++) {
			AbstractCoordinateTransformation [] linePair = new AbstractCoordinateTransformation[2];
			
			if (coordinatesType == ABSOLUTE_COORDINATES) {
				linePair[0] = new AbsoluteCoordinateTransformation();
				linePair[1] = new AbsoluteCoordinateTransformation();
			} else if (coordinatesType == FRACTIONAL_COORDINATES) {
				linePair[0] = new FractionCoordinateTransformation();
				linePair[1] = new FractionCoordinateTransformation();
			}
			
			lineSegments.add(linePair);
		}
	}

	@Override
	public double getAspectRatio() {
		return -1.0;
	}
	
	public void setLineConnectionPacmanMode(boolean pacmanMode) {
		if (pacmanMode)
			lineConnectionMode = 2;
		else
			lineConnectionMode = 1;
	}
	
	private void initializeList(int count) {
		lineSegments = new ArrayList<AbstractCoordinateTransformation[]>();
		for (int i = 0;i < count;i ++) {
			AbstractCoordinateTransformation [] linePair = new AbstractCoordinateTransformation[2];
			
			if (coordinatesType == ABSOLUTE_COORDINATES) {
				linePair[0] = new AbsoluteCoordinateTransformation();
				linePair[1] = new AbsoluteCoordinateTransformation();
			} else if (coordinatesType == FRACTIONAL_COORDINATES) {
				linePair[0] = new FractionCoordinateTransformation();
				linePair[1] = new FractionCoordinateTransformation();
			}
			
			lineSegments.add(linePair);
		}
	}

	@Override
	public void renderComponent(Graphics2D g2d) {
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
		if (! isRenderComponent())
			return;
		
		if (alphaChannel <= 0.0)
			return;
		
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
		if (lineSegments == null)
			return;
		
		int left = (int)getBoundaryLeft();
		int top = (int)getBoundaryTop();
		int width = (int)getBoundaryWidth();
		int height = (int)getBoundaryHeight();

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
		Stroke savedStroke = null;
		if ((lineWidth != null) && ((lineWidthTransformType == ABSOLUTE_COORDINATES) || (lineWidthTransformType == FRACTIONAL_COORDINATES))) {
			savedStroke = g2d.getStroke();
			int stroke = 1;
			if (lineWidthTransformType == ABSOLUTE_COORDINATES)
				stroke = lineWidth.intValue();
			else if (lineWidthTransformType == FRACTIONAL_COORDINATES)
				stroke = (int)(lineWidth * (width + height) / 2);
			
			g2d.setStroke(new BasicStroke(stroke));
		}
		
		Color lastBgColor = null;
		Color lastColor = null;
		
		if (alphaChannel <= 1.0) {
			lastBgColor = g2d.getBackground();
			lastColor = g2d.getColor();
			
			Color c = new Color(lastBgColor.getRed(), lastBgColor.getGreen(), lastBgColor.getBlue(), (int)(255.0 * alphaChannel));
			g2d.setBackground(c);
			c = new Color(lastColor.getRed(), lastColor.getGreen(), lastColor.getBlue(), (int)(255.0 * alphaChannel));
			g2d.setColor(c);
		}
		
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
		if (lineSegments.size() > 0) {
			double cutoff = Math.sqrt(width * width + height * height) * 2.0 / 3.0;

			for (int i = 0;i < lineSegments.size();i ++) {
				AbstractCoordinateTransformation [] linePair = lineSegments.get(i);
				
				Point p1 = linePair[0].transform(left, top, width, height);
				Point p2 = linePair[1].transform(left, top, width, height);
				
				if (lineConnectionMode == 1) {
					g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
				} else {
					double xDelta = p1.x - p2.x;
					double yDelta = p1.y - p2.y;
					double length = Math.sqrt(xDelta*xDelta + yDelta*yDelta);
					if (length < cutoff) {
						g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
					} else {
						int fromX = p1.x - left;
						int fromY = p1.y;
						int toX = p2.x - left;
						int toY = p2.y;
						int intermediateX = toX;
						int intermediateY = toY;
						
						if (toX > fromX) {
							intermediateX -= width;
							intermediateY = fromY - (int)(((double)(fromY - intermediateY) / (double)(fromX - intermediateX))*(double)fromX);
							g2d.drawLine(p1.x, p1.y, left, intermediateY);
							g2d.drawLine(left + width - 1, intermediateY, p2.x, p2.y);
						}
						else {
							intermediateX += width;
//							intermediateY = toY - (int)(((double)(intermediateY - fromY) / (double)(intermediateX - width))*(double)(width - fromX));
							intermediateY = fromY - (int)(((double)(fromY - intermediateY) / (double)(intermediateX - fromX))*(double)(width - fromX));
							g2d.drawLine(p1.x, p1.y, left + width - 1, intermediateY);
							g2d.drawLine(left, intermediateY, p2.x, p2.y);
						}
					}
				}
			}
		}
		
		if (lastBgColor != null)
			g2d.setBackground(lastBgColor);
		if (lastColor != null)
			g2d.setColor(lastColor);
		
		if (savedStroke != null)
			g2d.setStroke(savedStroke);
=======
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
		for (int i = 0;i < lineSegments.size();i ++) {
			AbstractCoordinateTransformation [] linePair = lineSegments.get(i);
			
			Point p1 = linePair[0].transform(left, top, width, height);
			Point p2 = linePair[1].transform(left, top, width, height);
			g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
		}
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
		
		if (lastBgColor != null)
			g2d.setBackground(lastBgColor);
		if (lastColor != null)
			g2d.setColor(lastColor);
		
		if (savedStroke != null)
			g2d.setStroke(savedStroke);
<<<<<<< HEAD
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
	}

	public double getX1() {
		return lineSegments.get(0)[0].getX();
	}

	public void setX1(double x) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[0].setX(x);
	}

	public double getY1() {
		return lineSegments.get(0)[0].getY();
	}

	public void setY1(double y) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[0].setY(y);
	}

	public double getX2() {
		return lineSegments.get(0)[1].getX();
	}

	public void setX2(double x) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[1].setX(x);
	}

	public double getY2() {
		return lineSegments.get(0)[1].getY();
	}

	public void setY2(double y) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[1].setY(y);
	}

	
	public void setX1Array(double [] xArray) {
		if (xArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != xArray.length)
			initializeList(xArray.length);
		
		for (int i = 0;i < xArray.length;i ++) {
			lineSegments.get(i)[0].setX(xArray[i]);
		}
	}
	
	public void setY1Array(double [] yArray) {
		if (yArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != yArray.length)
			initializeList(yArray.length);
		
		for (int i = 0;i < yArray.length;i ++) {
			lineSegments.get(i)[0].setY(yArray[i]);
		}
	}
	
	public void setX2Array(double [] xArray) {
		if (xArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != xArray.length)
			initializeList(xArray.length);
		
		for (int i = 0;i < xArray.length;i ++) {
			lineSegments.get(i)[1].setX(xArray[i]);
		}
	}
	
	public void setY2Array(double [] yArray) {
		if (yArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != yArray.length)
			initializeList(yArray.length);
		
		for (int i = 0;i < yArray.length;i ++) {
			lineSegments.get(i)[1].setY(yArray[i]);
		}
	}
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)

	public Double getLineWidth() {
		return lineWidth;
	}

	public int getLineWidthTransformType() {
		return lineWidthTransformType;
	}

	public void setLineWidth(Double lineWidth, int lineWidthTransformType) {
		this.lineWidth = lineWidth;
		this.lineWidthTransformType = lineWidthTransformType;
	}
	
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
}
