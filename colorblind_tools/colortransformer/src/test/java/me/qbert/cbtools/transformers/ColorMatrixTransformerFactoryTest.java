package me.qbert.cbtools.transformers;

import java.awt.Color;
import java.util.List;

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

public class ColorMatrixTransformerFactoryTest {
	private static void printColor(Color color) {
		System.out.println("R: " + color.getRed() + ", G: " + color.getGreen() + ", B: " + color.getBlue());
	}
	
	public static void main(String[] args) {
		List<String> transformNames = ColorMatrixTransformerFactory.getTransformNames();
		
		for (String name : transformNames) {
			System.out.println("Transform name: " + name);
		}
		
		try {
			ColorTransformerI transformer = ColorMatrixTransformerFactory.getColorTransformer("Normal");
			
			Color srcPixel = new Color(230, 64, 90);
			
			Color newPixel = transformer.transformColor(srcPixel);
			
			printColor(srcPixel);
			printColor(newPixel);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
