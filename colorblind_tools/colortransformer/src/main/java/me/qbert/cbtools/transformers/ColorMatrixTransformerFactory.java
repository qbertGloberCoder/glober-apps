package me.qbert.cbtools.transformers;

import java.util.Arrays;
import java.util.List;

import me.qbert.cbtools.transformers.impl.ColorMatrixTransformer;
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

public class ColorMatrixTransformerFactory {
	/*
	  Taken from: https://github.com/MaPePeR/jsColorblindSimulator/blob/master/colorblind.js
	  
// Source: http://web.archive.org/web/20081014161121/http://www.colorjack.com/labs/colormatrix/
// Another Source: https://www.reddit.com/r/gamedev/comments/2i9edg/code_to_create_filters_for_colorblind/
//
/ * Comment on http://kaioa.com/node/75#comment-247 states that:
ColorMatrix? Nope, won't work.
You're right, the ColorMatrix version is very simplified, and not accurate. I created that color matrix one night (http://www.colorjack.com/labs/colormatrix/)
and since then it's shown up many places... I should probably take that page down before it spreads more! Anyways, it gives you an idea of what it might look
like, but for the real thing...
As far as a simple script to simulate color blindness, this one does the best job:
http://www.nofunc.com/Color_Blindness_Library/ — It uses "confusion lines" within the XYZ color space to calculate values (this one is in Javascript, and should be easy to convert to python).
There are a few other methods, and no one really knows exactly what it would look like... these are all generalizations of a small sample, set against the masses.
* /
var ColorMatrixMatrixes = {
    Normal:       {
        R:[100,      0,     0],
        G:  [0,    100,      0],
        B:  [0,      0, 100 / *Fixed: was in the wrong spot in the original version * /]},
    Protanopia:   {
        R:[56.667, 43.333,  0],
        G:[55.833, 44.167,  0],
        B: [0,     24.167, 75.833]},
    Protanomaly:  {
        R:[81.667, 18.333,  0],
        G:[33.333, 66.667,  0],
        B: [0,     12.5,   87.5]},
    Deuteranopia: {
        R:[62.5, 37.5,  0],
        G:[70,   30,    0],
        B: [0,   30,   70]},
    Deuteranomaly:{
        R:[80,     20,      0],
        G:[25.833, 74.167,  0],
        B: [0,     14.167, 85.833]},
    Tritanopia:   {
        R:[95,  5,      0],
        G: [0, 43.333, 56.667],
        B: [0, 47.5,   52.5]},
    Tritanomaly:  {
        R:[96.667, 3.333,   0],
        G: [0,     73.333, 26.667],
        B: [0,     18.333, 81.667]},
    Achromatopsia:{
        R:[29.9, 58.7, 11.4],
        G:[29.9, 58.7, 11.4],
        B:[29.9, 58.7, 11.4]},
    Achromatomaly:{
        R:[61.8, 32,    6.2],
        G:[16.3, 77.5,  6.2],
        B:[16.3, 32.0, 51.6]}
};	  
	 */
	
	// Need a better way to initialize this... like a database for example.
	
	private static final int NORMAL_TRANSFORM_INDEX = 0;
	private static final String [] TRANSFORM_MODE_NAMES = {
			"Normal", "Protanopia", "Protanomaly", "Deuteranopia", "Deuteranomaly",
			"Tritanopia", "Tritanomaly", "Achromatopsia", "Achromatomaly" };

	
	private static final double [][][] TRANSFORM_MODE_MATRIX =
		{
		 { {100, 0, 0},				{0, 100, 0},			{0, 0, 100} },
		 { {56.667, 43.333, 0},		{55.833, 44.167, 0},	{0, 24.167, 75.833} },
         { {81.667, 18.333, 0}, 	{33.333, 66.667, 0}, 	{0, 12.5, 87.5} },
   		 { {62.5, 37.5, 0}, 		{70, 30, 0}, 			{0, 30, 70} },
   		 { {80, 20, 0}, 			{25.833, 74.167, 0}, 	{0, 14.167, 85.833} },
   		 { {95, 5, 0}, 				{0, 43.333, 56.667}, 	{0, 47.5, 52.5} },
   		 { {96.667, 3.333, 0}, 		{0, 73.333, 26.667}, 	{0, 18.333, 81.667} },
   		 { {29.9, 58.7, 11.4}, 		{29.9, 58.7, 11.4}, 	{29.9, 58.7, 11.4} },
   		 { {61.8, 32, 6.2}, 		{16.3, 77.5, 6.2}, 		{16.3, 32.0, 51.6} }
   		};
	
	private static final List<String> modeNames;
	
	static {
		modeNames = Arrays.asList(TRANSFORM_MODE_NAMES);
	}

	public static List<String> getTransformNames() {
		return modeNames;
	}
	
	public static ColorTransformerI getColorTransformer(String transformName) throws Exception {
		int index = modeNames.indexOf(transformName);
		
		if (index < 0)
			throw new Exception("Transform cannot be found");
		
		// If the user selected "normal", it will be faster to use the "NoOp" transformer
		if (TRANSFORM_MODE_NAMES[NORMAL_TRANSFORM_INDEX].equals(transformName))
			return new NoOpColorTransformer();
		
		return new ColorMatrixTransformer(TRANSFORM_MODE_MATRIX[index], true);
	}
	
}
