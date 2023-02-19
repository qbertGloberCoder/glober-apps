package me.qbert.skywatch.astro.impl;

import java.util.Calendar;

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

public class SolarObjects extends AbstractCelestialObject {
	private static final Logger logger = LogManager.getLogger(SolarObjects.class.getName());

	public class SunObjectBuilder extends AbstractCelestialObjectBuilder {
		public SunObjectBuilder() {
		}

		@Override
		protected SolarObjects getInstance() {
			return new SolarObjects();
		}
	}
	
	public class SolarSystemCoordinate {
		double x;
		double y;
		double z;
		
		public double getX() {
			return x;
		}
		
		public double getY() {
			return y;
		}
		
		public double getZ() {
			return z;
		}
	}
	
	private class ObjectInformation {
		double ra;
		double dec;
		double rvec;
		
		SolarSystemCoordinate coordinate;
		SolarSystemCoordinate coordinateFromEarth;
	}
	
	private class ObjectSettings {
		double a;
		double e;
		double i;
		double O;
		double w;
		double L;
	}
	

	private static final double RADS = Math.PI/180;
    private static final double EPS  = 1.0e-12;
    private static final double DEGS = 180/Math.PI;

	/*
	 * This should be helpful:
	 * https://codepen.io/lulunac27/full/NRoyxE
	 */
	
    public static final String [] OBJECT_LIST = {"Sun", "Mercury", "Venus",  "Mars", "Jupiter", "Saturn ",  "Uranus", "Neptune", "Pluto"};
    public static final double ONE_AU = 149597871.0;
    public static final double MAX_AU = ONE_AU * 40.0;
	
	private int objectIndex = 0;
	private boolean debug = false;
	
	private double eclipticOffset = 0;
	private double ellipticOffset = 1.0;
	private double obliquityOffset = 0;
	
	SolarSystemCoordinate [] objectCoordinates = new SolarSystemCoordinate[9];
	SolarSystemCoordinate [] objectCoordinatesFromEarth = new SolarSystemCoordinate[9];
	
	private double [] calculatedDeclinations = new double[9];
	private double [] calculatedRightAscensions = new double[9];
	
    private ObjectSettings earth;
	
	// Not entirely happy with this design
	private SolarObjects() {
	}

	private SunObjectBuilder createBuilder() {
		return new SunObjectBuilder();
	}
	
	public static SunObjectBuilder create() {
		return new SolarObjects().createBuilder();
	}
	
	
	public void setEclipticOffset(double eclipticOffset) {
		this.eclipticOffset = eclipticOffset;
	}
	
	public void setEllipticOffset(double ellipticOffset) {
		this.ellipticOffset = ellipticOffset;
	}
	
	public void setObliquityOffset(double obliquityOffset) {
		this.obliquityOffset = obliquityOffset;
	}
	
	private void debug(String line) {
		if (debug)
			System.out.println(line);
	}
	
	private void debugVar(String var, double val) {
		debug("VAR " + var + " = " + val);
	}
	
	
	// day number to/from J2000 (Jan 1.5, 2000)
	public double day_number( Calendar calendar )
	{
		int y = calendar.get(Calendar.YEAR);
		int m = calendar.get(Calendar.MONTH) + 1;
		int d = calendar.get(Calendar.DAY_OF_MONTH);
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int mins = calendar.get(Calendar.MINUTE);
		int seconds = calendar.get(Calendar.SECOND);
		double dstOffset = (double)(calendar.get(Calendar.DST_OFFSET) / 3600000.0);
		double tzOffset = (double)(calendar.get(Calendar.ZONE_OFFSET) / 3600000.0);
		double timezoneOffset = tzOffset + dstOffset;
		
		
	    double h = (double)hour + (double)mins/60.0 + (double)seconds/3600.0 - timezoneOffset;
	    double rv = 367*y 
	           - Math.floor(7*(y + Math.floor((m + 9)/12))/4) 
	           + Math.floor(275*m/9) + d - 730531.5 + h/24;
	    return rv;
	}
	
	private String d2(int i) {
		return String.format("%02d", i);
	}
	
	private double abs_floor( double x )
	{
	    double r;
	    if (x >= 0.0) r = Math.floor(x);
	    else          r = Math.ceil(x);
	    return r;
	}

	private double mod2pi( double x )
	{
	    double b = x/(2*Math.PI);
	    double a = (2*Math.PI)*(b - abs_floor(b));  
	    if (a < 0) a = (2*Math.PI) + a;
	    return a;
	}
	
	// Compute the elements of the orbit for planet-i at day number-d
	// result is returned in structure p
	private ObjectSettings mean_elements( int planetIndex, double julianDate )
	{
	    double century = julianDate/36525;                    // centuries since J2000

	    ObjectSettings planet = new ObjectSettings();
	    
	    switch (planetIndex)
	    {
	    case 0: // Earth/Sun
	        planet.a = 1.00000011 - 0.00000005*century;
	        planet.e = 0.01671022 - 0.00003804*century;
	        planet.i = (  0.00005 -    46.94*century/3600)*RADS;
	        planet.O = (-11.26064 - 18228.25*century/3600)*RADS;
	        planet.w = (102.94719 +  1198.28*century/3600)*RADS;
	        planet.L = mod2pi((100.46435 + 129597740.63*century/3600)*RADS);
	        break;
	    case 1: // Mercury
	        planet.a = 0.38709893 + 0.00000066*century;
	        planet.e = 0.20563069 + 0.00002527*century;
	        planet.i = ( 7.00487  -  23.51*century/3600)*RADS;
	        planet.O = (48.33167  - 446.30*century/3600)*RADS;
	        planet.w = (77.45645  + 573.57*century/3600)*RADS;
	        planet.L = mod2pi((252.25084 + 538101628.29*century/3600)*RADS);
	        break;
	    case 2: // Venus
	        planet.a = 0.72333199 + 0.00000092*century;
	        planet.e = 0.00677323 - 0.00004938*century;
	        planet.i = (  3.39471 -   2.86*century/3600)*RADS;
	        planet.O = ( 76.68069 - 996.89*century/3600)*RADS;
	        planet.w = (131.53298 - 108.80*century/3600)*RADS;
	        planet.L = mod2pi((181.97973 + 210664136.06*century/3600)*RADS);
	        break;
	    case 3: // Mars
	        planet.a = 1.52366231 - 0.00007221*century;
	        planet.e = 0.09341233 + 0.00011902*century;
	        planet.i = (  1.85061 -   25.47*century/3600)*RADS;
	        planet.O = ( 49.57854 - 1020.19*century/3600)*RADS;
	        planet.w = (336.04084 + 1560.78*century/3600)*RADS;
	        planet.L = mod2pi((355.45332 + 68905103.78*century/3600)*RADS);
	        break;
	    case 4: // Jupiter
	        planet.a = 5.20336301 + 0.00060737*century;
	        planet.e = 0.04839266 - 0.00012880*century;
	        planet.i = (  1.30530 -    4.15*century/3600)*RADS;
	        planet.O = (100.55615 + 1217.17*century/3600)*RADS;
	        planet.w = ( 14.75385 +  839.93*century/3600)*RADS;
	        planet.L = mod2pi((34.40438 + 10925078.35*century/3600)*RADS);
	        break;
	    case 5: // Saturn
	        planet.a = 9.53707032 - 0.00301530*century;
	        planet.e = 0.05415060 - 0.00036762*century;
	        planet.i = (  2.48446 +    6.11*century/3600)*RADS;
	        planet.O = (113.71504 - 1591.05*century/3600)*RADS;
	        planet.w = ( 92.43194 - 1948.89*century/3600)*RADS;
	        planet.L = mod2pi((49.94432 + 4401052.95*century/3600)*RADS);
	        break;
	    case 6: // Uranus
	        planet.a = 19.19126393 + 0.00152025*century;
	        planet.e =  0.04716771 - 0.00019150*century;
	        planet.i = (  0.76986  -    2.09*century/3600)*RADS;
	        planet.O = ( 74.22988  - 1681.40*century/3600)*RADS;
	        planet.w = (170.96424  + 1312.56*century/3600)*RADS;
	        planet.L = mod2pi((313.23218 + 1542547.79*century/3600)*RADS);
	        break;
	    case 7: // Neptune
	        planet.a = 30.06896348 - 0.00125196*century;
	        planet.e =  0.00858587 + 0.00002510*century;
	        planet.i = (  1.76917  -   3.64*century/3600)*RADS;
	        planet.O = (131.72169  - 151.25*century/3600)*RADS;
	        planet.w = ( 44.97135  - 844.43*century/3600)*RADS;
	        planet.L = mod2pi((304.88003 + 786449.21*century/3600)*RADS);
	        break;
	    case 8: // Pluto
	        planet.a = 39.48168677 - 0.00076912*century;
	        planet.e =  0.24880766 + 0.00006465*century;
	        planet.i = ( 17.14175  +  11.07*century/3600)*RADS;
	        planet.O = (110.30347  -  37.33*century/3600)*RADS;
	        planet.w = (224.06676  - 132.25*century/3600)*RADS;
	        planet.L = mod2pi((238.92881 + 522747.90*century/3600)*RADS);
	        break;
	    default:
	        System.out.println("function mean_elements() failed!");
	        return null;
	    }
	    
	    planet.e *= ellipticOffset;
	    
	    return planet;
	}

	private double true_anomaly( double M, double e )
	{
	    double V, E1;

	    // initial approximation of eccentric anomaly
	    double E = M + e*Math.sin(M)*(1.0 + e*Math.cos(M));

	    do                                   // iterate to improve accuracy
	    {
	        E1 = E;
	        E = E1 - (E1 - e*Math.sin(E1) - M)/(1 - e*Math.cos(E1));
	    }
	    while (Math.abs( E - E1 ) > EPS);

	    // convert eccentric anomaly to true anomaly
	    V = 2*Math.atan(Math.sqrt((1 + e)/(1 - e))*Math.tan(0.5*E));

	    if (V < 0) V = V + (2*Math.PI);      // modulo 2pi
	    
	    return V;
	}

	private ObjectInformation get_coord( int planetIndex, double dayNumber )
	{
		return get_coord(mean_elements(planetIndex, dayNumber), planetIndex);
	}
	
	private ObjectInformation get_coord( ObjectSettings planet, int planetIndex )
	{
	    double ap = planet.a;
	    double ep = planet.e;
	    double ip = planet.i;
	    double op = planet.O;
	    double pp = planet.w;
	    double lp = planet.L;

	    double ae = earth.a;
	    double ee = earth.e;
	    double ie = earth.i;
	    double oe = earth.O;
	    double pe = earth.w;
	    double le = earth.L; 
	    
	    // position of Earth in its orbit
	    double me = mod2pi(le - pe);
	    double ve = true_anomaly(me, ee);
	    double re = ae*(1 - ee*ee)/(1 + ee*Math.cos(ve));
	    
	    // heliocentric rectangular coordinates of Earth
	    double xe = re*Math.cos(ve + pe);
	    double ye = re*Math.sin(ve + pe);
	    double ze = 0.0;
	    
	    // position of planet in its orbit
	    double mp = mod2pi(lp - pp);
	    double vp = true_anomaly(mp, planet.e);
	    double rp = ap*(1 - ep*ep)/(1 + ep*Math.cos(vp));
	    
	    // heliocentric rectangular coordinates of planet
	    double xh = rp*(Math.cos(op)*Math.cos(vp + pp - op) - Math.sin(op)*Math.sin(vp + pp - op)*Math.cos(ip));
	    double yh = rp*(Math.sin(op)*Math.cos(vp + pp - op) + Math.cos(op)*Math.sin(vp + pp - op)*Math.cos(ip));
	    double zh = rp*(Math.sin(vp + pp - op)*Math.sin(ip));

	    ObjectInformation object = new ObjectInformation();
	    object.coordinate = new SolarSystemCoordinate();
	    object.coordinateFromEarth = new SolarSystemCoordinate();
	    
	    object.coordinate.x = xh;
	    object.coordinate.y = yh;
	    object.coordinate.z = zh;
	    
	    if (planetIndex == 0)                          // earth --> compute sun
	    {
	        xh = 0;
	        yh = 0;
	        zh = 0;
	    }
	    
	    // convert to geocentric rectangular coordinates
	    double xg = xh - xe;
	    double yg = yh - ye;
	    double zg = zh - ze;
	    
	    // rotate around x axis from ecliptic to equatorial coords
	    double ecl = 23.439281*RADS;            //value for J2000.0 frame
	    double xeq = xg;
	    double yeq = yg*Math.cos(ecl) - zg*Math.sin(ecl);
	    double zeq = yg*Math.sin(ecl) + zg*Math.cos(ecl);
	    
	    object.coordinateFromEarth.x = xeq;
	    object.coordinateFromEarth.y = yeq;
	    object.coordinateFromEarth.z = zeq;
	    
	    
	    // find the RA and DEC from the rectangular equatorial coords
	    object.ra   = mod2pi(Math.atan2(yeq, xeq))*DEGS; 
	    object.dec  = Math.atan(zeq/Math.sqrt(xeq*xeq + yeq*yeq))*DEGS;
	    object.rvec = Math.sqrt(xeq*xeq + yeq*yeq + zeq*zeq);
	    
	    return object;
	}

	private String ha2str( double x )
	{
	    if ((x < 0)||(360 < x)) debug("function ha2str() range error!");
	    
	    double ra = x/15;                       // degrees to hours
	    int h = (int)(Math.floor(ra));
	    double m = 60*(ra - h);
	    return String.format("%3d h %4.1f m", h, m);
	}

	private String dec2str( double x )
	{
	    if ((x < -90)||(+90 < x)) debug("function dec2str() range error!");
	    
	    double dec = Math.abs(x);
	    String sgn = (x < 0) ? "-" : " ";
	    int d = (int)(Math.floor(dec));
	    double m = 60*(dec - d);
	    return String.format("%s%2dh %4.1fm", sgn, d, m);
	}
	
	private double julianDay (int date, int month, int year, double timezoneOffset)
	{
	    if (month<=2) {month=month+12; year=year-1;}
	    return (int)(365.25*year) + (int)(30.6001*(month+1)) - 15 + 1720996.5 + date - timezoneOffset/24.0;
	} 

	@Override
	public void recompute() {
		// F2
		double julianDate = observationTime.getJulianDate();

		logger.trace("Recompute SOLR objects for julian date: " + julianDate);

	    // compute day number for date/time
	    double dn = day_number( observationTime.getTime() );

		double days = julianDate-2451545;
		double gmst = modulus(18.697374558+24.065709824419*days,24);
		double omega = 125.04-0.052954*julianDate;
		double l = 280.47+0.98565*julianDate;
		double e = 23.4393-0.0000004*julianDate;
		double deltaW = -0.000319*Math.sin(Math.toRadians(omega))-0.000024*Math.sin(Math.toRadians(2*l));
		double eqEq = deltaW*Math.cos(Math.toRadians(e));
		double gast = gmst+eqEq;

		/*
Standard: Dec = -15.16163557934776, ra = -6.7195060638147766, oh lat = -15.16163557934776, oh lon = -68.28049393618522
solar calc (0): Dec = -15.255990026012283, ra = -6.410548199594757, oh lat = 344.7440099739877, oh lon = 291.41054819959476
               : alt = 29.46292531934822, az = 172.89332598798546
solar calc (1): Dec = -19.208051299672828, ra = 18.256558167429546, oh lat = 340.79194870032717, oh lon = 266.74344183257045
               : alt = 23.671522733380097, az = 198.8450553907744
solar calc (2): Dec = -16.529088456297796, ra = 31.71570876967337, oh lat = 343.4709115437022, oh lon = 253.28429123032663
               : alt = 22.054094463423674, az = 212.94051290651475
		 * 
		 */
		
		earth = mean_elements(0, dn);
		
	    // compute location of objects
	    for (int p = 0; p < OBJECT_LIST.length; p++)  
	    {
	    	ObjectInformation obj;
	    	
	    	if (p == 0)
	    		obj = get_coord(earth, p);   
	    	else
	    		obj = get_coord(p, dn);

	    	//	    	debug(String.format("%10s%11s%12s%s", args))
//	    	coord_to_horizon(now, obj.ra, obj.dec, lat, lon, h);

//	    	double r = Math.sqrt(obj.x * obj.x + obj.y * obj.y + obj.z * obj.z);
//	    	System.out.println("x = " + obj.x + ", y = " + obj.y + ", z = " + obj.z + ", r = " + r);
	    	
	    	objectCoordinates[p] = obj.coordinate;
	    	objectCoordinatesFromEarth[p] = obj.coordinateFromEarth;
	    	
		    calculatedDeclinations[p] = obj.dec;
		    calculatedRightAscensions[p] = ((gast*15)-obj.ra)+location.getLongitude();

	    	debug(OBJECT_LIST[p] + "  " + ha2str(obj.ra) + "  " + dec2str(obj.dec) + "  " + Double.toString(obj.rvec));
	    }
	}
	
	private double fixRaAndLon(double angle) {
		while (angle < -180.0)
			angle += 360.0;
		while (angle > 180.0)
			angle -= 360.0;
		
		return angle;
	}

	@Override
	public void setObjectIndex(int objectIndex) {
		if ((objectIndex >= 0) && (objectIndex < calculatedRightAscensions.length))
			this.objectIndex = objectIndex;
	}
	
	@Override
	public ObjectDirectionRaDec getCelestialSphereLocation() {
		return makeRaDec(fixRaAndLon(location.getLongitude() - calculatedRightAscensions[objectIndex]), calculatedDeclinations[objectIndex]);
	}

	@Override
	public ObjectDirectionRaDec getCurrentDirection() {
		return makeRaDec(fixRaAndLon(calculatedRightAscensions[objectIndex]), calculatedDeclinations[objectIndex]);
	}

	@Override
	public GeoLocation getEarthPositionOverhead() {
		return makeGeoLocation(calculatedDeclinations[objectIndex], fixRaAndLon(location.getLongitude() - calculatedRightAscensions[objectIndex]));
	}
}
