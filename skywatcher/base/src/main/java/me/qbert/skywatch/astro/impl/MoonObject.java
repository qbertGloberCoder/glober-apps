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

public class MoonObject extends AbstractCelestialObject {
	private static final Logger logger = LogManager.getLogger(MoonObject.class.getName());

    /**
     * PI * 2
     */
    public static final double PI2 = Math.PI * 2.0;

    /**
     * Arc-Seconds per Radian.
     */
    public static final double ARCS = Math.toDegrees(3600.0);
    

    public class MoonObjectBuilder extends AbstractCelestialObjectBuilder {
		public MoonObjectBuilder() {
		}

		@Override
		protected MoonObject getInstance() {
			return new MoonObject();
		}
	}

	private double alpha_hour;
	private double delta_deg;
	
	private double rightAscension;
	
	// Not entirely happy with this design
	private MoonObject() {
	}

	private MoonObjectBuilder createBuilder() {
		return new MoonObjectBuilder();
	}
	
	public static MoonObjectBuilder create() {
		return new MoonObject().createBuilder();
	}
	
    /*
    
    THIS ONE is taken from here: https://github.com/shred/commons-suncalc
    
    Accuracy

		Astronomical calculations are far more complex than throwing a few numbers into an obscure formula and then getting a fully
		accurate result. There is always a tradeoff between accuracy and computing time.

		This library has its focus on getting acceptable results at low cost, so it can also run on mobile devices, or devices with a
		low computing power. The results have an accuracy of about a minute, which should be good enough for common applications (like
		sunrise/sunset timers), but is probably not sufficient for astronomical purposes.

		If you are looking for the highest possible accuracy, you are looking for a different library.
    
   */
	@Override
	public void recompute() {
		double time_jd;
		
		double julianDate = observationTime.getJulianDate();
		double julianCentury = observationTime.getJulianCentury();

		logger.trace("Recompute moon object for julian date: " + julianDate);

        time_jd = (julianDate - 2451545.0) / 36525.0; // See Meeus 22.1

        double T = julianCentury;
        double L0 =       frac(0.606433 + 1336.855225 * T);
        double l  = PI2 * frac(0.374897 + 1325.552410 * T);
        double ls = PI2 * frac(0.993133 +   99.997361 * T);
        double D  = PI2 * frac(0.827361 + 1236.853086 * T);
        double F  = PI2 * frac(0.259086 + 1342.227825 * T);

        double dL = 22640 * Math.sin(l)
                  -  4586 * Math.sin(l - 2 * D)
                  +  2370 * Math.sin(2 * D)
                  +   769 * Math.sin(2 * l)
                  -   668 * Math.sin(ls)
                  -   412 * Math.sin(2 * F)
                  -   212 * Math.sin(2 * l - 2 * D)
                  -   206 * Math.sin(l + ls - 2 * D)
                  +   192 * Math.sin(l + 2 * D)
                  -   165 * Math.sin(ls - 2 * D)
                  -   125 * Math.sin(D)
                  -   110 * Math.sin(l + ls)
                  +   148 * Math.sin(l - ls)
                  -    55 * Math.sin(2 * F - 2 * D);

        double S  = F + (dL + 412 * Math.sin(2 * F) + 541 * Math.sin(ls)) / ARCS;
        double h  = F - 2 * D;
        double N  =  -526 * Math.sin(h)
                  +    44 * Math.sin(l + h)
                  -    31 * Math.sin(-l + h)
                  -    23 * Math.sin(ls + h)
                  +    11 * Math.sin(-ls + h)
                  -    25 * Math.sin(-2 * l + F)
                  +    21 * Math.sin(-l + F);

        double L = Math.toDegrees(PI2 * frac(L0 + dL / 1296.0e3));
        double B = Math.toDegrees((18520.0 * Math.sin(S) + N) / ARCS);

        double dt = 385000.6 - 20905.0 * Math.cos(l);		
		double t = (julianDate - 2451545.0 ) / 36525; 
		
		//obliquity of ecliptic:
		double eps = 23.0 + 26.0/60.0 + 21.448/3600.0 - (46.8150*T+ 0.00059*T*T- 0.001813*T*T*T)/3600;

		double X = Math.cos(Math.toRadians(B))*Math.cos(Math.toRadians(L));
		double Y = Math.cos(Math.toRadians(eps))*Math.cos(Math.toRadians(B))*Math.sin(Math.toRadians(L)) - Math.sin(Math.toRadians(eps))*Math.sin(Math.toRadians(B));
		double Z = Math.sin(Math.toRadians(eps))*Math.cos(Math.toRadians(B))*Math.sin(Math.toRadians(L)) + Math.cos(Math.toRadians(eps))*Math.sin(Math.toRadians(B));
		double R = Math.sqrt(1-Z*Z);

		double delta = (180/Math.PI)*Math.atan(Z/R); // in degrees

		double RA = (24/Math.PI)*Math.atan(Y/(X+R)); // in hours
	
		double theta0 = modulus(280.46061837 + 360.98564736629*(julianDate-2451545.0) + 0.000387933*t*t - t*t*t/38710000.0, 360.0); // degrees 
		double theta = theta0 + location.getLongitude();

		rightAscension = L;
		alpha_hour = modulus(theta - (RA*15.) + 180.0, 360.0) - 180.0;
		delta_deg = delta;
	}

    private static double frac(double a) {
        return a % 1.0;
    }
    
    @Override
    public ObjectDirectionRaDec getCelestialSphereLocation() {
    	return makeRaDec(rightAscension, delta_deg);
    }

	@Override
	public ObjectDirectionRaDec getCurrentDirection() {
		return makeRaDec(alpha_hour, delta_deg);
	}

	@Override
	public GeoLocation getEarthPositionOverhead() {
		return makeGeoLocation(delta_deg, location.getLongitude() - alpha_hour);
	}
}
