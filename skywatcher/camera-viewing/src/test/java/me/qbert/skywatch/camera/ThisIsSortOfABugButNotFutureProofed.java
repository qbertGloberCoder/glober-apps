package me.qbert.skywatch.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.astro.service.SunPrecession;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;
import me.qbert.skywatch.astro.service.AbstractPrecession.PrecessionData;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ArcRenderer;

public class ThisIsSortOfABugButNotFutureProofed {
	public static void main(String[] args) {
		SunPrecession sunPrecession;
		
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(45, -75);
		sunPrecession = new SunPrecession(location, true);
		sunPrecession.setShowAsAnalemma(true);
		
		ObservationTime time = new ObservationTime();
		
		GeoCalculator geoCalc = new GeoCalculator();
		
		System.out.println("Analemma mode? " + sunPrecession.isShowAsAnalemma());
		try {
			time.initTime(TimeZone.getDefault());
			time.setLocalTime(2026, 8, 15, 13, 0, 0);
			
	        List<PrecessionData> precessionTimes = sunPrecession.calculatePrecession(time);
	        for (PrecessionData data : precessionTimes) {
//	        	System.out.println("Entry: ALT=" + data.getAltAz().getAltitude() + ", AZIMUTH=" + data.getAltAz().getAzimuth());
	        	ObjectDirectionRaDec raDec = new ObjectDirectionRaDec();
	        	raDec.setDeclination(data.getGroundPosition().getLatitude());
	        	raDec.setRightAscension(location.getLongitude() - data.getGroundPosition().getLongitude());
//	        	System.out.println("   or: RA=" + raDec.getRightAscension() + ", DEC=" + raDec.getDeclination());
	        	ObjectDirectionAltAz localDirection = geoCalc.raDeclinationToAltitudeAzimuth(raDec, location);
	        	System.out.println("   or: ALT=" + localDirection.getAltitude() + ", AZIMUTH=" + localDirection.getAzimuth());
	        }
        } catch (Exception e) {
        	e.printStackTrace();
        }
		
	}
}
