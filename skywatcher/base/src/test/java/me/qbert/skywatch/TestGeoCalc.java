package me.qbert.skywatch;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

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

public class TestGeoCalc {
	private void assertForTest(String failMessage, ObjectDirectionRaDec computed, ObjectDirectionRaDec expected) throws AssertionFailedError {
		Assert.assertTrue(failMessage, (((Math.abs(computed.getRightAscension() - expected.getRightAscension()) < 0.0005) &&
									(Math.abs(computed.getDeclination() - expected.getDeclination()) < 0.0005)) ? true : false));
	}
	
	private void testAltAzRaDecConversions() {
		GeoCalculator geoCalc = new GeoCalculator();
		
		ObjectDirectionRaDec objectRaDec = new ObjectDirectionRaDec();
		ObserverLocation location = new ObserverLocation();
		
		objectRaDec.setDeclination(15);
		objectRaDec.setRightAscension(-50);
		location.setGeoLocation(20, 30);
		ObjectDirectionAltAz altAz = geoCalc.raDeclinationToAltitudeAzimuth(objectRaDec, location);
		ObjectDirectionRaDec reverseRaDec = geoCalc.altAzToRaDec(altAz, location);
		
		System.out.println("For: " + location.getLatitude() + ", " + location.getLongitude());
		System.out.println("and: " + objectRaDec.getRightAscension() + " ra, " + objectRaDec.getDeclination() + " dec");
		System.out.println("got: " + altAz.getAltitude() + " alt, " + altAz.getAzimuth() + " az");
		System.out.println("rev: " + reverseRaDec.getRightAscension() + " ra, " + reverseRaDec.getDeclination() + " dec");
		assertForTest("reverse convert failed", reverseRaDec, objectRaDec);
		
		objectRaDec.setDeclination(-12.2534);
		objectRaDec.setRightAscension(30.12243);
		altAz = geoCalc.raDeclinationToAltitudeAzimuth(objectRaDec, location);
		reverseRaDec = geoCalc.altAzToRaDec(altAz, location);
		
		System.out.println("For: " + location.getLatitude() + ", " + location.getLongitude());
		System.out.println("and: " + objectRaDec.getRightAscension() + " ra, " + objectRaDec.getDeclination() + " dec");
		System.out.println("got: " + altAz.getAltitude() + " alt, " + altAz.getAzimuth() + " az");
		System.out.println("rev: " + reverseRaDec.getRightAscension() + " ra, " + reverseRaDec.getDeclination() + " dec");
		assertForTest("reverse convert failed", reverseRaDec, objectRaDec);
		
		objectRaDec.setDeclination(-24.2534);
		objectRaDec.setRightAscension(95.12243);
		location.setGeoLocation(-90, 40);
		altAz = geoCalc.raDeclinationToAltitudeAzimuth(objectRaDec, location);
		reverseRaDec = geoCalc.altAzToRaDec(altAz, location);
		
		System.out.println("For: " + location.getLatitude() + ", " + location.getLongitude());
		System.out.println("and: " + objectRaDec.getRightAscension() + " ra, " + objectRaDec.getDeclination() + " dec");
		System.out.println("got: " + altAz.getAltitude() + " alt, " + altAz.getAzimuth() + " az");
		System.out.println("rev: " + reverseRaDec.getRightAscension() + " ra, " + reverseRaDec.getDeclination() + " dec");
		assertForTest("reverse convert failed", reverseRaDec, objectRaDec);

		objectRaDec.setDeclination(-2.454);
		objectRaDec.setRightAscension(0.24225612);
		location.setGeoLocation(5, 130);
		altAz = geoCalc.raDeclinationToAltitudeAzimuth(objectRaDec, location);
		reverseRaDec = geoCalc.altAzToRaDec(altAz, location);
		
		System.out.println("For: " + location.getLatitude() + ", " + location.getLongitude());
		System.out.println("and: " + objectRaDec.getRightAscension() + " ra, " + objectRaDec.getDeclination() + " dec");
		System.out.println("got: " + altAz.getAltitude() + " alt, " + altAz.getAzimuth() + " az");
		System.out.println("rev: " + reverseRaDec.getRightAscension() + " ra, " + reverseRaDec.getDeclination() + " dec");
		assertForTest("reverse convert failed", reverseRaDec, objectRaDec);
	}
	
	public static void main(String[] args) {
		TestGeoCalc tester = new TestGeoCalc();
		
		tester.testAltAzRaDecConversions();
	}
}
