package me.qbert.cbtools.transformers.impl;

import java.awt.Color;

import me.qbert.cbtools.transformers.ColorTransformerI;

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

public class ColorMatrixTransformer implements ColorTransformerI {
	public static final int MATRIX_X = 3;
	public static final int MATRIX_Y = 3;
	private static final double [][] defaultMatrix = { {100, 0, 0},				{0, 100, 0},			{0, 0, 100} };
	
	private double [][] matrix = new double[MATRIX_Y][MATRIX_X];
	
	public ColorMatrixTransformer() throws Exception {
		this(defaultMatrix, true);
	}
	
	public ColorMatrixTransformer(double [][] colorMatrix, boolean expressedAsPercent) throws Exception {
		if (colorMatrix.length != matrix.length)
			throwUsageException();
		
		updateColorMatrix(colorMatrix, ((expressedAsPercent) ? 100.0 : 1.0));
	}
	
	private static void throwUsageException() throws Exception {
		throw new Exception("The color matrix must be " + MATRIX_X + " by "+ MATRIX_Y);
	}
	
	public void updateColorMatrix(double [][] colorMatrix, double maximumValue) throws Exception {
		for (int y = 0;y < matrix.length;y ++) {
			if (colorMatrix[y].length != matrix[y].length)
				throwUsageException();
			
			for (int x = 0;x < matrix[y].length;x ++) {
				matrix[y][x] = colorMatrix[y][x] / maximumValue;
			}
		}
	}
	
	@Override
	public Color transformColor(Color sourcePixel) {
		int [] newColors = new int[MATRIX_Y];
		
//		System.out.println("R: " + sourcePixel.getRed() + ", G: " + sourcePixel.getGreen() + ", B: " + sourcePixel.getBlue());
		for (int i = 0;i < MATRIX_Y;i ++) {
			newColors[i] = (int)(sourcePixel.getRed() * matrix[i][0] + sourcePixel.getGreen() * matrix[i][1] + sourcePixel.getBlue() * matrix[i][2]);
//			System.out.println("Rm: " + matrix[i][0] + ", Gm: " + matrix[i][1] + ", Bm: " + matrix[i][0]);
//			System.out.println("??? " + i + "??? = " + newColors[i]);
		}

		Color newColor = new Color(newColors[0], newColors[1], newColors[2]);
		
		return newColor;
	}

}
