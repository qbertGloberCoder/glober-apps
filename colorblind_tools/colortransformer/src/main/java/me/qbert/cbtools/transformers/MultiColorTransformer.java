package me.qbert.cbtools.transformers;

import java.awt.Color;
import java.util.List;

import me.qbert.cbtools.transformers.impl.NoOpColorTransformer;

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

public class MultiColorTransformer implements ColorTransformerI {
	
	private ColorTransformerI worker = new NoOpColorTransformer();

	public List<String> getTransformNames() {
		return ColorMatrixTransformerFactory.getTransformNames();
	}
	
	public void changeTransformer(String transformName) throws Exception {
		worker = ColorMatrixTransformerFactory.getColorTransformer(transformName);
	}
	
	@Override
	public Color transformColor(Color sourcePixel) {
		return worker.transformColor(sourcePixel);
	}

}
