package me.qbert.skywatch.astro.impl;

import java.util.Calendar;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import me.qbert.skywatch.astro.impl.SolarObjects.SolarSystemCoordinate;
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
		private double x;
		private double y;
		private double z;
		
		public double getX() {
			return x;
		}
		
		public double getY() {
			return y;
		}
		
		public double getZ() {
			return z;
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			
			sb.append("x=");
			sb.append(x);
			sb.append("\ny=");
			sb.append(y);
			sb.append("\rz=");
			sb.append(z);
			
			return sb.toString();
		}
	}
	
	private class ObjectInformation {
		double ra;
		double dec;
		double rvec;
		
		SolarSystemCoordinate coordinate;
		SolarSystemCoordinate coordinateFromEarth;
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			
			sb.append("ra=");
			sb.append(ra);
			sb.append("\ndec=");
			sb.append(dec);
			sb.append("\rvec=");
			sb.append(rvec);
			
			sb.append("\rcoordinate: ");
			sb.append(coordinate.toString());
			sb.append("\rcoordinate from earth: ");
			sb.append(coordinateFromEarth.toString());
			
			return sb.toString();
		}
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
    public static final double SOLAR_SYSTEM_IN_AUS = 40.0;
    public static final double MAX_AU = ONE_AU * SOLAR_SYSTEM_IN_AUS;
	
	private int objectIndex = 0;
	private boolean debug = false;
	
	private double eclipticOffset = 0;
	private double ellipticOffset = 1.0;
	private double obliquityOffset = 0;
	
	SolarSystemCoordinate [] objectCoordinates = new SolarSystemCoordinate[9];
	SolarSystemCoordinate [] objectCoordinatesFromEarth = new SolarSystemCoordinate[9];
	
	private double [] calculatedDeclinations = new double[9];
	private double [] calculatedCelestialRightAscensions = new double[9];
	private double [] calculatedRightAscensions = new double[9];
	
	private ObjectInformation [] objectProfiles = new ObjectInformation[9];
	
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
	    logger.trace("abs_floor(x=" + x + ")");
	    if (x >= 0.0) r = Math.floor(x);
	    else          r = Math.ceil(x);
	    logger.trace("if (x >= 0.0) r = Math.floor(x) else r = Math.ceil(x) >> " + r);
	    return r;
	}

	private double mod2pi( double x )
	{
		logger.trace("mod2pi(x=" + x + ")");
		
	    double b = x/(2*Math.PI);
	    logger.trace("b = x/(2*Math.PI) >> " + b);
	    double a = (2*Math.PI)*(b - abs_floor(b));  
	    logger.trace("a = (2*Math.PI)*(b - abs_floor(b)) >> " + a);
	    if (a < 0) a = (2*Math.PI) + a;
	    logger.trace("if (a < 0) a = (2*Math.PI) + a >> " + a);
	    return a;
	}
	
	// Compute the elements of the orbit for planet-i at day number-d
	// result is returned in structure p
	private ObjectSettings mean_elements( int planetIndex, double julianDate )
	{
	    double century = julianDate/36525;                    // centuries since J2000
	    
	    logger.trace("mean_elements(planetIndex=" + planetIndex + ", julianDate=" + julianDate + ")");


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
	    
	    logger.trace("planet.a >> " + planet.a);
	    logger.trace("planet.e >> " + planet.e);
	    logger.trace("planet.i >> " + planet.i);
	    logger.trace("planet.O >> " + planet.O);
	    logger.trace("planet.w >> " + planet.w);
	    logger.trace("planet.L >> " + planet.L);

	    planet.e *= ellipticOffset;
	    
	    logger.trace("eclipticOffset >> " + ellipticOffset);
	    logger.trace("planet.e *= ellipticOffset >> " + planet.e);
	    
	    return planet;
	}

	private double true_anomaly( double M, double e )
	{
	    double V, E1;

	    logger.trace("true_anomaly(M=" + M + ", e=" + e + ")");
	    
	    // initial approximation of eccentric anomaly
	    double E = M + e*Math.sin(M)*(1.0 + e*Math.cos(M));

	    logger.trace("E = M + e*Math.sin(M)*(1.0 + e*Math.cos(M)) >> " + E);
	    
	    do                                   // iterate to improve accuracy
	    {
	        E1 = E;
	        logger.trace("(while) E1 >> " + E);
	        E = E1 - (E1 - e*Math.sin(E1) - M)/(1 - e*Math.cos(E1));
	        logger.trace("(while) E = E1 - (E1 - e*Math.sin(E1) - M)/(1 - e*Math.cos(E1)) >> " + E);
	        
	        logger.trace("(while (Math.abs( E - E1 ) >> " + (Math.abs( E - E1 )) + "> EPS >> " + EPS + ")");
	    }
	    while (Math.abs( E - E1 ) > EPS);

	    // convert eccentric anomaly to true anomaly
	    V = 2*Math.atan(Math.sqrt((1 + e)/(1 - e))*Math.tan(0.5*E));
	    
	    logger.trace("V = 2*Math.atan(Math.sqrt((1 + e)/(1 - e))*Math.tan(0.5*E)) >> " + V);

	    if (V < 0) V = V + (2*Math.PI);      // modulo 2pi

	    logger.trace("if (V < 0) V = V + (2*Math.PI) >> " + V);
	    
	    return V;
	}

	private ObjectInformation get_coord( int planetIndex, double dayNumber )
	{
		return get_coord(mean_elements(planetIndex, dayNumber), planetIndex);
	}
	
	private ObjectInformation get_coord( ObjectSettings planet, int planetIndex )
	{
	    logger.trace("get_coord(planet=[ (lots of values enumerated below) ], planetIndex=" + planetIndex + ")");

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
	    

	    logger.trace("(planet values)");
	    logger.trace("ap >> " + ap);
	    logger.trace("ep >> " + ep);
	    logger.trace("ip >> " + ip);
	    logger.trace("op >> " + op);
	    logger.trace("pp >> " + pp);
	    logger.trace("lp >> " + lp);
	    
	    logger.trace("(earth values)");
	    logger.trace("ae >> " + ae);
	    logger.trace("ep >> " + ee);
	    logger.trace("ip >> " + ie);
	    logger.trace("op >> " + oe);
	    logger.trace("pp >> " + pe);
	    logger.trace("lp >> " + le);

	    // position of Earth in its orbit
	    double me = mod2pi(le - pe);
	    logger.trace("me = mod2pi(le - pe) >> " + me);
	    double ve = true_anomaly(me, ee);
	    logger.trace("ve = true_anomaly(me, ee) >> " + ve);
	    double re = ae*(1 - ee*ee)/(1 + ee*Math.cos(ve));
	    logger.trace("re = ae*(1 - ee*ee)/(1 + ee*Math.cos(ve)) >> " + re);

	    // heliocentric rectangular coordinates of Earth
	    double xe = re*Math.cos(ve + pe);
	    logger.trace("xe = re*Math.cos(ve + pe) >> " + xe);
	    double ye = re*Math.sin(ve + pe);
	    logger.trace("ye = re*Math.sin(ve + pe) >> " + ye);
	    double ze = 0.0;
	    logger.trace("ze = 0.0");
	    
	    // position of planet in its orbit
	    double mp = mod2pi(lp - pp);
	    double vp = true_anomaly(mp, planet.e);
	    double rp = ap*(1 - ep*ep)/(1 + ep*Math.cos(vp));

	    logger.trace("mp = mod2pi(lp - pp) >> " + mp);
	    logger.trace("vp = true_anomaly(mp, planet.e) >> " + vp);
	    logger.trace("rp = ap*(1 - ep*ep)/(1 + ep*Math.cos(vp)) >> " + rp);
	    
	    // heliocentric rectangular coordinates of planet
	    double xh = rp*(Math.cos(op)*Math.cos(vp + pp - op) - Math.sin(op)*Math.sin(vp + pp - op)*Math.cos(ip));
	    logger.trace("xh = rp*(Math.cos(op)*Math.cos(vp + pp - op) - Math.sin(op)*Math.sin(vp + pp - op)*Math.cos(ip)) >> " + xh);
	    double yh = rp*(Math.sin(op)*Math.cos(vp + pp - op) + Math.cos(op)*Math.sin(vp + pp - op)*Math.cos(ip));
	    logger.trace("yh = rp*(Math.sin(op)*Math.cos(vp + pp - op) + Math.cos(op)*Math.sin(vp + pp - op)*Math.cos(ip)) >> " + yh);
	    double zh = rp*(Math.sin(vp + pp - op)*Math.sin(ip));
	    logger.trace("zh = rp*(Math.sin(vp + pp - op)*Math.sin(ip)) >> " + zh);

	    ObjectInformation object = new ObjectInformation();
	    object.coordinate = new SolarSystemCoordinate();
	    object.coordinateFromEarth = new SolarSystemCoordinate();
	    
	    object.coordinate.x = xh;
	    object.coordinate.y = yh;
	    object.coordinate.z = zh;
	    
	    logger.trace("[xh, yh, zh] >> object.coordinate");
	    
	    if (planetIndex == 0)                          // earth --> compute sun
	    {
	        xh = 0;
	        yh = 0;
	        zh = 0;
	    }
	    
	    // convert to geocentric rectangular coordinates
	    double xg = xh - xe;
	    logger.trace("xg = xh - xe >> " + xg);
	    double yg = yh - ye;
	    logger.trace("yg = yh - ye >> " + yg);
	    double zg = zh - ze;
	    logger.trace("zg = zh - ze >> " + zg);
	    
	    // rotate around x axis from ecliptic to equatorial coords
	    double ecl = 23.439281*RADS;            //value for J2000.0 frame
	    logger.trace("ecl = 23.439281*RADS >> " + ecl);
	    double xeq = xg;
	    logger.trace("xeq = xg >> " + xeq);
	    double yeq = yg*Math.cos(ecl) - zg*Math.sin(ecl);
	    logger.trace("yeq = yg*Math.cos(ecl) - zg*Math.sin(ecl) >> " + yeq);
	    double zeq = yg*Math.sin(ecl) + zg*Math.cos(ecl);
	    logger.trace("zeq = yg*Math.sin(ecl) + zg*Math.cos(ecl) >> " + zeq);
	    
	    object.coordinateFromEarth.x = xeq;
	    object.coordinateFromEarth.y = yeq;
	    object.coordinateFromEarth.z = zeq;
	    
	    logger.trace("[xeq, yeq, zeq] >> object.coordinateFromEarth");
	    
	    
	    // find the RA and DEC from the rectangular equatorial coords
	    object.ra   = mod2pi(Math.atan2(yeq, xeq))*DEGS; 
	    logger.trace("object.ra   = mod2pi(Math.atan2(yeq, xeq))*DEGS >> " + object.ra);
	    object.dec  = Math.atan(zeq/Math.sqrt(xeq*xeq + yeq*yeq))*DEGS;
	    logger.trace("object.dec  = Math.atan(zeq/Math.sqrt(xeq*xeq + yeq*yeq))*DEGS >> " + object.dec);
	    object.rvec = Math.sqrt(xeq*xeq + yeq*yeq + zeq*zeq);
	    logger.trace("object.rvec = Math.sqrt(xeq*xeq + yeq*yeq + zeq*zeq) >> " + object.rvec);
	    
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
	
	@Override
	public void recompute() {
		// F2
		double julianDate = observationTime.getJulianDate();

		logger.trace("Recompute SOLR objects for julian date: " + julianDate);

	    // compute day number for date/time
	    double dn = julianDate - 2451545.0;
	    
	    logger.trace("");
	    
		double days = julianDate-2451545;
	    logger.trace("days = julianDate-2451545 >> " + days);
		double gmst = modulus(18.697374558+24.065709824419*days,24);
	    logger.trace("gmst = modulus(18.697374558+24.065709824419*days,24) >> " + gmst);
		double omega = 125.04-0.052954*julianDate;
	    logger.trace("omega = 125.04-0.052954*julianDate >> " + omega);
		double l = 280.47+0.98565*julianDate;
	    logger.trace("l = 280.47+0.98565*julianDate >> " + l);
		double e = 23.4393-0.0000004*julianDate;
	    logger.trace("e = 23.4393-0.0000004*julianDate >> " + e);
		double deltaW = -0.000319*Math.sin(Math.toRadians(omega))-0.000024*Math.sin(Math.toRadians(2*l));
	    logger.trace("deltaW = -0.000319*Math.sin(Math.toRadians(omega))-0.000024*Math.sin(Math.toRadians(2*l)) >> " + deltaW);
		double eqEq = deltaW*Math.cos(Math.toRadians(e));
	    logger.trace("eqEq = deltaW*Math.cos(Math.toRadians(e)) >> " + eqEq);
		double gast = gmst+eqEq;
	    logger.trace("gast = gmst+eqEq >> " + gast);

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
	    	
	    	logger.trace("p >> " + p);
	    	logger.trace("OBJECT_LIST[p] >> " + OBJECT_LIST[p]);
	    	
	    	if (p == 0)
	    		obj = get_coord(earth, p);   
	    	else
	    		obj = get_coord(p, dn);
	    	
	    	objectProfiles[p] = obj;

	    	//	    	debug(String.format("%10s%11s%12s%s", args))
//	    	coord_to_horizon(now, obj.ra, obj.dec, lat, lon, h);

//	    	double r = Math.sqrt(obj.x * obj.x + obj.y * obj.y + obj.z * obj.z);
//	    	System.out.println("x = " + obj.x + ", y = " + obj.y + ", z = " + obj.z + ", r = " + r);
	    	
	    	objectCoordinates[p] = obj.coordinate;
	    	objectCoordinatesFromEarth[p] = obj.coordinateFromEarth;
	    	
		    calculatedDeclinations[p] = obj.dec;
		    calculatedCelestialRightAscensions[p] = obj.ra;
		    calculatedRightAscensions[p] = ((gast*15)-obj.ra)+location.getLongitude();
		    
		    logger.trace("calculatedDeclinations[p] = obj.dec >> " + calculatedDeclinations[p]); 
		    logger.trace("calculatedCelestialRightAscensions[p] >> " + calculatedCelestialRightAscensions[p]); 
		    logger.trace("calculatedRightAscensions[p] = ((gast*15)-obj.ra)+location.getLongitude() >> " + calculatedRightAscensions[p]); 

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
	
	public SolarSystemCoordinate[] getSolarSystemObjectCoordinates() {
		return objectCoordinates;
	}

	public SolarSystemCoordinate[] getEarthObjectCoordinates() {
		return objectCoordinatesFromEarth;
	}

	@Override
	public void setObjectIndex(int objectIndex) {
		if ((objectIndex >= 0) && (objectIndex < calculatedRightAscensions.length))
			this.objectIndex = objectIndex;
	}
	
	@Override
	public ObjectDirectionRaDec getCelestialSphereLocation() {
		logger.trace("[" + objectIndex + "]" + objectProfiles[objectIndex].toString());
		return makeRaDec(fixRaAndLon(calculatedCelestialRightAscensions[objectIndex]), calculatedDeclinations[objectIndex]);
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
