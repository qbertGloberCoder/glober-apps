package me.qbert.skywatch.dao;

import java.io.BufferedReader;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import me.qbert.skywatch.model.StarCoordinate;

public class StarsCoordinateDao {
	private static HashMap<String, StarCoordinate> starMap = null;
	private static ArrayList<StarCoordinate> loadedStars = null;

	public static ArrayList<StarCoordinate> getLoadedStars() {
		if (loadedStars == null) {
			starMap = new HashMap<String, StarCoordinate>();
			loadedStars = new ArrayList<StarCoordinate>();
			
			try {
				loadBasicStars();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return loadedStars;
	}
	
	private static void loadBasicStars() throws IOException {
		
		String stars = getResourceFileAsString("stars.txt");
		String [] starLines = stars.split("[\r\n]");
		for (String line : starLines) {
			if (! line.startsWith("#")) {
				String [] elements = line.split(",");
				StarCoordinate starThing = new StarCoordinate();
				
				try
				{
    				starThing.setName(elements[0]);
    				starThing.setDesignation(elements[1]);
    				Double d = new Double(elements[10]);
    				starThing.setRightAscension(d.doubleValue());
    				d = new Double(elements[11]);
    				starThing.setDeclination(d.doubleValue());
    				String mag = elements[7].replaceFirst(" .*$", "");
    				if (mag.equals("-"))
    					mag = "0.5";
    				d = new Double(mag);
    				starThing.setMagnification(d.doubleValue());
    				starThing.setGroupLevel(1);
    				starThing.setVisible(false);
    				
    				if (starMap.get(starThing.getDesignation()) == null) {
    					starMap.put(starThing.getDesignation(), starThing);
    					loadedStars.add(starThing);
    				}
				}
				catch (NumberFormatException e) {
					System.out.println("Unable to parse one of: " + elements[7] + ", " + elements[10] + ", " + elements[11]);
				}
			}
		}
	}

	static String getResourceFileAsString(String fileName) throws IOException {
	    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
	    try (InputStream is = classLoader.getResourceAsStream(fileName)) {
	        if (is == null) return null;
	        try (InputStreamReader isr = new InputStreamReader(is);
	             BufferedReader reader = new BufferedReader(isr)) {
	            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
	        }
	    }
	}}
