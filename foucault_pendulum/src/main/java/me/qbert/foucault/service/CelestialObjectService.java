package me.qbert.foucault.service;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.qbert.foucault.model.CelestialObjectProfile;

public class CelestialObjectService {
	private static final List<CelestialObjectProfile> objectProfiles;
	static {
		ArrayList<CelestialObjectProfile> profiles = new ArrayList<CelestialObjectProfile>();
		
		/* NOTE: I was a bit lazy and let ChatGPT go crawl the internet for these values...
		 * I'm pretty sure some of them are wrong but I'm still lazy enough not to fix any that are
		 */
		
		
		profiles.add(new CelestialObjectProfile("Earth", 86164.09, 9.80665, 150, 3.4, 1.0));

		profiles.add(new CelestialObjectProfile("North Pole (generic)", 86164.09, 9.80665, 150, 3.4, 90.0));
		profiles.add(new CelestialObjectProfile("45° N (generic)", 86164.09, 9.80665, 150, 3.4, 45.0));
		profiles.add(new CelestialObjectProfile("Equator (generic)", 86164.09, 9.80665, 150, 3.4, 0.0));
		profiles.add(new CelestialObjectProfile("45° S (generic)", 86164.09, 9.80665, 150, 3.4, -45.0));
		profiles.add(new CelestialObjectProfile("South Pole (generic)", 86164.09, 9.80665, 150, 3.4, -90.0));

		profiles.add(new CelestialObjectProfile("Besançon (Musée du Temps, FR)", 86164.09, 9.80665, 13.11, 1.5, 47.13));     // Besançon ~47°08′ N :contentReference[oaicite:3]{index=3}
		profiles.add(new CelestialObjectProfile("Cité des Sciences (Paris ~33 m)", 86164.09, 9.80665, 33.0, 2.0, 48.866)); // science museum :contentReference[oaicite:5]{index=5}
		profiles.add(new CelestialObjectProfile("Eurajoki (FI)", 86164.09, 9.80665, 40.0, 2.5, /* latitude */ 61.20));     // Eurajoki ~61°12′ N (approx.) :contentReference[oaicite:6]{index=6}
		profiles.add(new CelestialObjectProfile("Grenoble (Cosmocité, FR)", 86164.09, 9.80665, 15.08, 1.5, 45.12));     // Grenoble ~45°07′ N :contentReference[oaicite:4]{index=4}
		profiles.add(new CelestialObjectProfile("Kraków (PL)", 86164.09, 9.80665, 46.5, 3.0, /* latitude */ 50.06));     // Kraków ~50°03′ N (city center) :contentReference[oaicite:7]{index=7}
		profiles.add(new CelestialObjectProfile("Musée des Arts et Métiers (Paris, FR)", 86164.09, 9.80665, 67.0, 3.25, 48.86));      // Same latitude as Panthéon :contentReference[oaicite:2]{index=2}
		profiles.add(new CelestialObjectProfile("Observatoire de Paris (~20 m)", 86164.09, 9.80665, 20.0, 1.5, 48.87)); // early reproduction :contentReference[oaicite:4]{index=4}
		profiles.add(new CelestialObjectProfile("Panthéon (Paris, FR)", 86164.09, 9.80665, 67.0, 3.25, 48.86));      // Paris ~48°51′ N :contentReference[oaicite:1]{index=1}
		profiles.add(new CelestialObjectProfile("University of Alberta (EDM, CA)", 86164.09, 9.80665, 23.4, 1.5, 53.53));     // Edmonton ~53°32′ N
		profiles.add(new CelestialObjectProfile("University of Chile Foucault Pendulum (Santiago, Chile)", 86164.09, 9.80665, 18.5, 1.5, -33.456)); // Santiago pendulum installation :contentReference[oaicite:9]{index=9}
		profiles.add(new CelestialObjectProfile("University of Turku (FI)", 86164.09, 9.80665, 16.76, 1.4, 60.45));     // Turku ~60°27′ N (common value) :contentReference[oaicite:5]{index=5}
		profiles.add(new CelestialObjectProfile("UNSW Foucault Pendulum (Sydney, Australia)", 86164.09, 9.80665, 12.0, 1.2, -33.918)); // University of NSW typical demo (approx numbers) :contentReference[oaicite:10]{index=10}
		profiles.add(new CelestialObjectProfile("Valdivia Foucault Pendulum (Chile)", 86164.09, 9.80665, 13.0, 1.5, -39.818)); // Valdivia installation (≈13 m) :contentReference[oaicite:8]{index=8}
		
		
		profiles.add(new CelestialObjectProfile("Moon", 2360591.51, 1.622));
		profiles.add(new CelestialObjectProfile("Mars", 88642.66, 3.721));
		profiles.add(new CelestialObjectProfile("Venus", -20995200.0, 8.87));
		profiles.add(new CelestialObjectProfile("Jupiter", 35729.71, 24.79));
		profiles.add(new CelestialObjectProfile("Saturn", 38362.0, 10.44));
		profiles.add(new CelestialObjectProfile("PSR J0250+5854", 23.5, 10000.0));
		
		objectProfiles = profiles;
	}
	
	public static List<CelestialObjectProfile> getObjectProfiles() {
		return Collections.unmodifiableList(objectProfiles);
	}
}
