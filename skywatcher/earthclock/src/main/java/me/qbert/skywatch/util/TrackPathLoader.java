package me.qbert.skywatch.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.stream.Collectors;

import me.qbert.skywatch.model.GeoLocation;

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

public class TrackPathLoader {
	public static ArrayList<GeoLocation> loadTracks(String fileName) {
		ArrayList<GeoLocation> locations = new ArrayList<GeoLocation>();
		
		String waypoints;
		try {
			waypoints = getResourceFileAsString(fileName);
			if (waypoints == null)
				return null;
		} catch (IOException e) {
			return null;
		}
		String [] waypointLines = waypoints.split("[\r\n]");
		for (String line : waypointLines) {		
			String [] elements = line.split(",");
			try {
				double lat = Double.parseDouble(elements[1]);
				double lon = Double.parseDouble(elements[2]);
				
				GeoLocation loc = new GeoLocation();
				loc.setGeoLocation(lat, lon);
				
				locations.add(loc);
				
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		
		return locations;
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
	}
}
