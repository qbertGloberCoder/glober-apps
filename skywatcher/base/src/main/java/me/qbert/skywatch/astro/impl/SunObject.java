package me.qbert.skywatch.astro.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import me.qbert.skywatch.model.GeoLocation;
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

public class SunObject extends AbstractCelestialObject {
	private static final Logger logger = LogManager.getLogger(SunObject.class.getName());

	public class SunObjectBuilder extends AbstractCelestialObjectBuilder {
		public SunObjectBuilder() {
		}

		@Override
		protected SunObject getInstance() {
			return new SunObject();
		}
	}
	private double hourAngle;
	
	private double declination;
	
	// Not entirely happy with this design
	private SunObject() {
	}

	private SunObjectBuilder createBuilder() {
		return new SunObjectBuilder();
	}
	
	public static SunObjectBuilder create() {
		return new SunObject().createBuilder();
	}
	
	/*
	 Taken from the "NOAA Solar Calculations" spreadsheet
	 */
	@Override
	public void recompute() {
		// F2
		double julianDate = observationTime.getJulianDate();

		logger.trace("Recompute Sun object for julian date: " + julianDate);

		// G2
		double julianCentury = observationTime.getJulianCentury();
		// I2
		double sunDegree = modulus(280.46646+julianCentury*(36000.76983+julianCentury*0.0003032),360);
		// J2
		double meanAnomoly = 357.52911+julianCentury*(35999.05029-0.0001537*julianCentury);
		// K2
		double eccentricEarthOrbit = 0.016708634-julianCentury*(0.000042037+0.0000001267*julianCentury);
		// L2
		double sunEqOfCenter = Math.sin(Math.toRadians(meanAnomoly))*(1.914602-julianCentury*(0.004817+0.000014*julianCentury))+Math.sin(Math.toRadians(2*meanAnomoly))*(0.019993-0.000101*julianCentury)+Math.sin(Math.toRadians(3*meanAnomoly))*0.000289;
		// M2
		double sunTrueLong = sunDegree + sunEqOfCenter;
		// N2
		double sunTrueAnomoloy = meanAnomoly + sunEqOfCenter;
		// O2
		double sunRadVector = (1.000001018*(1-eccentricEarthOrbit*eccentricEarthOrbit))/(1+eccentricEarthOrbit*Math.cos(Math.toRadians(sunTrueAnomoloy)));
		// P2
		double sunAppLong = sunTrueLong-0.00569-0.00478*Math.sin(Math.toRadians(125.04-1934.136*julianCentury));

		// Q2
		double meanObliqEcliptic = 23+(26+((21.448-julianCentury*(46.815+julianCentury*(0.00059-julianCentury*0.001813))))/60)/60;
		// R2
		double obliqueCorrected = meanObliqEcliptic+0.00256*Math.cos(Math.toRadians(125.04-1934.136*julianCentury));
		// S2
		double sunRightAscension = Math.toDegrees(Math.atan2(Math.cos(Math.toRadians(obliqueCorrected))*Math.sin(Math.toRadians(sunAppLong)), Math.cos(Math.toRadians(sunAppLong))));
		// T2
		double sunDeclination = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(obliqueCorrected))*Math.sin(Math.toRadians(sunAppLong))));
		this.declination = sunDeclination;
		// U2
		double varY = Math.tan(Math.toRadians(obliqueCorrected/2))*Math.tan(Math.toRadians(obliqueCorrected/2));
		// V2
		double eqOfTime = 4*Math.toDegrees(varY*Math.sin(2*Math.toRadians(sunDegree))-2*eccentricEarthOrbit*Math.sin(Math.toRadians(meanAnomoly))+4*eccentricEarthOrbit*varY*Math.sin(Math.toRadians(meanAnomoly))*Math.cos(2*Math.toRadians(sunDegree))-0.5*varY*varY*Math.sin(4*Math.toRadians(sunDegree))-1.25*eccentricEarthOrbit*eccentricEarthOrbit*Math.sin(2*Math.toRadians(meanAnomoly)));
		// W2
		double haSunrise = Math.toDegrees(Math.acos(Math.cos(Math.toRadians(90.833))/(Math.cos(Math.toRadians(location.getLatitude()))*Math.cos(Math.toRadians(sunDeclination)))-Math.tan(Math.toRadians(location.getLatitude()))*Math.tan(Math.toRadians(sunDeclination))));
		// X2
		double solarNoon = (720-4*location.getLongitude()-eqOfTime+observationTime.getTimezoneAdjust()*60)/1440;
		// Y2
		double sunrise = solarNoon-haSunrise*4/1440;
		// Z2
		double sunset = solarNoon+haSunrise*4/1440;
		// AA2
		double sunlightDurationMins = 8*haSunrise;
		// AB2
		double trueSolrTimeMins=modulus(observationTime.getUtcTime()*1440+eqOfTime+4*location.getLongitude()-60*observationTime.getTimezoneAdjust(),1440);
		// AC2
		double hourAngle = (trueSolrTimeMins/4<0)?(trueSolrTimeMins/4+180) : (trueSolrTimeMins/4-180);
		this.hourAngle = hourAngle;
		// AD2
		double solarZenith = Math.toDegrees(Math.acos(Math.sin(Math.toRadians(location.getLatitude()))*Math.sin(Math.toRadians(sunDeclination))+Math.cos(Math.toRadians(location.getLatitude()))*Math.cos(Math.toRadians(sunDeclination))*Math.cos(Math.toRadians(hourAngle))));
		// AE2
		double sunElevation = 90-solarZenith;
		// AF2 ... THIS IS A HUGE MESS.... hopefully it's OK
		// =IF(AE2>85,0,IF(AE2>5,58.1/TAN(RADIANS(AE2))-0.07/POWER(TAN(RADIANS(AE2)),3)+0.000086/POWER(TAN(RADIANS(AE2)),5),IF(AE2>-0.575,1735+AE2*(-518.2+AE2*(103.4+AE2*(-12.79+AE2*0.711))),-20.772/TAN(RADIANS(AE2)))))/3600
		double refraction; // =IF
		if (sunElevation > 85.0)
			refraction = 0;
		else {
			if (sunElevation > 5.0)
				refraction = 58.1/Math.tan(Math.toRadians(sunElevation))-
						0.07/Math.pow(Math.tan(Math.toRadians(sunElevation)),3)+
						0.000086/Math.pow(Math.tan(Math.toRadians(sunElevation)),5);
			else {
				if (sunElevation > -0.575)
					refraction = 1735+sunElevation*(-518.2+sunElevation*(103.4+sunElevation*(-12.79+sunElevation*0.711)));
				else
					refraction = -20.772/Math.tan(Math.toRadians(sunElevation));
			}
		}
		refraction /= 3600.0;
		// AG2
		double correctedSolarElevation = sunElevation + refraction;
		// AH2
		double azimuth;
		if (hourAngle > 0.0)
			azimuth = modulus(Math.toDegrees(Math.acos(((Math.sin(Math.toRadians(location.getLatitude()))*Math.cos(Math.toRadians(solarZenith)))-Math.sin(Math.toRadians(sunDeclination)))/(Math.cos(Math.toRadians(location.getLatitude()))*Math.sin(Math.toRadians(solarZenith)))))+180,360);
		else
			azimuth = modulus(540-Math.toDegrees(Math.acos(((Math.sin(Math.toRadians(location.getLatitude()))*Math.cos(Math.toRadians(solarZenith)))-Math.sin(Math.toRadians(sunDeclination)))/(Math.cos(Math.toRadians(location.getLatitude()))*Math.sin(Math.toRadians(solarZenith))))),360);
	}

	@Override
	public ObjectDirectionRaDec getCurrentDirection() {
		return makeRaDec(hourAngle, declination);
	}

	@Override
	public GeoLocation getEarthPositionOverhead() {
		return makeGeoLocation(declination, location.getLongitude() - hourAngle);
	}
}
