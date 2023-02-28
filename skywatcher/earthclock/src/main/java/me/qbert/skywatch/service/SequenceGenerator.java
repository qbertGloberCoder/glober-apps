package me.qbert.skywatch.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.AbstractCelestialObject;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.astro.impl.SolarObjects.SolarSystemCoordinate;
import me.qbert.skywatch.dao.StarsCoordinateDao;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.CelestialAddress;
import me.qbert.skywatch.model.StarCoordinate;
import me.qbert.skywatch.service.sequence.AltitudeAdvanceSequence;
import me.qbert.skywatch.service.sequence.AnyAzimuthAdvanceSequence;
import me.qbert.skywatch.service.sequence.AzimuthAdvanceSequence;
import me.qbert.skywatch.service.sequence.SequenceJump;
import me.qbert.skywatch.service.sequence.SequencePause;
import me.qbert.skywatch.service.sequence.TimeAddSequence;
import me.qbert.skywatch.service.sequence.TimeSetSequence;

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

public class SequenceGenerator {
	private ObserverLocation myLocation = new ObserverLocation();
	private TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
	private ObservationTime time = new ObservationTime();
//	private CelestialObject sun;
	private CelestialObject moon;
	private AbstractCelestialObject solarObjects;
	private List<CelestialObject> stars;

	private int calendarField;
	
	private ArrayList<SequenceElementI> sequenceScript;
	private int sequenceIndex = 0;
	
	private Double advanceMilliseconds = null; //575989.47964420741;
	private double ts;
	
	private double lastDstOffset = -9999999;
	
	public SequenceGenerator() {
		try {
			time.initTime(TimeZone.getDefault());
			time.setCurrentTime();
			solarObjects = (AbstractCelestialObject) SolarObjects.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
//			sun = SunObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
			moon = MoonObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
			
			ArrayList<StarCoordinate> starCoordinates = StarsCoordinateDao.getLoadedStars();
			
			stars = new ArrayList<CelestialObject>();
			
			for (StarCoordinate star : starCoordinates) {
				CelestialAddress starAddress = new CelestialAddress();
				starAddress.setAddress(star.getRightAscension(), star.getDeclination());
				CelestialObject starObj = StarObject.create().setStarLocation(starAddress).setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
				transactionalListener.addListener(starObj);
				stars.add(starObj);
			}
		} catch (Exception e) {
			e.printStackTrace();
			time = null;
			solarObjects = null;
//			sun = null;
			moon = null;
		}
		
		transactionalListener.addListener(solarObjects);
//		transactionalListener.addListener(sun);
		transactionalListener.addListener(moon);
		
		myLocation.setGeoLocation(0.0, 0.0);
		
		sequenceScript = null;
//		initSequencer();
		
		try {
			advanceSequence(false);
		} catch (UninitializedObject e) {
			e.printStackTrace();
		}
	}
	
	public void abortScript() {
		if ((sequenceScript != null) && (sequenceIndex < sequenceScript.size())) {
			sequenceIndex = sequenceScript.size();
		}
	}
	
	public void loadFromScript(File scriptFile) {
		BufferedReader reader = null;
		sequenceScript = new ArrayList<SequenceElementI>();
		sequenceIndex = 0;

		Long l;
		Integer i;
		Double d;
		
		int labelIndex = 0;
		
		try {
			reader = new BufferedReader(new FileReader(scriptFile));
			String line;

			while ((line = reader.readLine()) != null) {
				System.out.println(line);
				
				if (line.startsWith("fulltime ")) {
					l = convertHardTimeToUtMillis(line.replaceFirst("^fulltime  *", ""));
					SequenceElementI si = new TimeSetSequence(time, l);
					sequenceScript.add(si);
				}
				
				if (line.startsWith("addtime ")) {
					i = new Integer(line.replaceFirst("^addtime  *", ""));
					SequenceElementI si = new TimeAddSequence(time, i);
					sequenceScript.add(si);
				}
				
				if (line.startsWith("nextalt ")) {
					String tmp = line.replaceFirst("^nextalt  *", "");
					
					SequenceElementI si = null;
					if (tmp.startsWith("sun ")) {
						d = new Double(tmp.replaceFirst("^sun  *", ""));
						si = new AltitudeAdvanceSequence(solarObjects, time, d);
					} else if (tmp.startsWith("moon ")) {
						d = new Double(tmp.replaceFirst("^moon  *", ""));
						si = new AltitudeAdvanceSequence(moon, time, d);
					}
					
					if (si != null)
						sequenceScript.add(si);
				}
				
				if (line.startsWith("nextaz ")) {
					String tmp = line.replaceFirst("^nextaz  *", "");
					
					SequenceElementI si = null;
					if (tmp.startsWith("sun ")) {
						d = new Double(tmp.replaceFirst("^sun  *", ""));
						si = new AzimuthAdvanceSequence(solarObjects, time, d);
					} else if (tmp.startsWith("moon ")) {
						d = new Double(tmp.replaceFirst("^moon  *", ""));
						si = new AzimuthAdvanceSequence(moon, time, d);
					}
					
					if (si != null)
						sequenceScript.add(si);
				}
				
				if (line.startsWith("nextanyaz ")) {
					String tmp = line.replaceFirst("^nextanyaz  *", "");
					
					String whichObj = tmp.replaceFirst(" .*$", "");
					String [] azimuthStrings = tmp.replaceFirst("^" + whichObj + "  *", "").split("  *");
					
					Double [] azimuths = new Double[azimuthStrings.length];
					
					for (int index = 0;index < azimuthStrings.length;index ++) {
						azimuths[index] = new Double(azimuthStrings[index]);
					}
					
					SequenceElementI si = null;
					if (whichObj.equals("sun")) {
						si = new AnyAzimuthAdvanceSequence(solarObjects, time, azimuths);
					} else if (whichObj.equals("moon")) {
						si = new AnyAzimuthAdvanceSequence(moon, time, azimuths);
					}
					
					if (si != null)
						sequenceScript.add(si);
				}
				
				if (line.equals("draw")) {
					SequenceElementI si = new SequencePause();
					sequenceScript.add(si);
				}
				
				if (line.startsWith("loop ")) {
					i = new Integer(line.replaceFirst("^loop  *", ""));
					System.out.println("??? loop to: " + (labelIndex - sequenceScript.size() - 1) + " and loop count: " + i);
					SequenceElementI si = new SequenceJump(labelIndex - sequenceScript.size() - 1, i);
					sequenceScript.add(si);
				}
				
				if (line.equals("label")) {
					labelIndex = sequenceScript.size();
				}
			}

			reader.close();
			reader = null;
		} catch (IOException e) {
			e.printStackTrace();
			sequenceScript = null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					sequenceScript = null;
				}
			}
		}
	}
	
	public void initSequencer() {
		sequenceScript = new ArrayList<SequenceElementI>();
		
		Long timeMillis = convertHardTimeToUtMillis("20230122000000");
		SequenceElementI si = new TimeSetSequence(time, timeMillis);
		sequenceScript.add(si);
		
		si = new SequencePause();
		sequenceScript.add(si);
		si = new TimeAddSequence(time, 60);
		sequenceScript.add(si);
		
		si = new SequenceJump(-3, 1440);
		sequenceScript.add(si);
	}
	
	public boolean isRunningScript() {
		return ((sequenceScript != null) && (sequenceIndex < sequenceScript.size()));
	}
	
	public boolean isInitialized() {
		return (moon != null);
	}
	
	public void setTime(String time) throws UninitializedObject {
		setTimeFromEncodedLong(convertHardTimeToUtMillis(time));
	}
	
	public void setTimeFromEncodedLong(Long timeInMills) throws UninitializedObject {
		setTime(convertHardTimeToUtMillis(timeInMills));
	}
	
	private Long convertHardTimeToUtMillis(String hardTime) {
		return convertHardTimeToUtMillis(new Long(hardTime));
	}
	
	private Long convertHardTimeToUtMillis(Long hardTime) {
		if (hardTime == null)
			return null;
		
		long l = hardTime.longValue();
		if ((l > 20000000000000L) && (l < 20500000000000L)) {
			int y = (int)(l / 10000000000L);
			l = l % 10000000000L;
			int m = (int)(l / 100000000L);
			l = l % 100000000L;
			int d = (int)(l / 1000000L);
			l = l % 1000000L;
			int h = (int)(l / 10000L);
			l = l % 10000L;
			int mn = (int)(l / 100L);
			l = l % 100L;
			int s = (int)(l);
			
			Calendar cal = Calendar.getInstance();
					
			cal.set(Calendar.YEAR, y);
			cal.set(Calendar.MONTH, m - 1);
			cal.set(Calendar.DAY_OF_MONTH, d);
			cal.set(Calendar.HOUR_OF_DAY, h);
			cal.set(Calendar.MINUTE, mn);
			cal.set(Calendar.SECOND, s);
			cal.set(Calendar.MILLISECOND, 0);
			
//			System.out.println("??? CONVERTED TO: " + y + ", " + m  + ", " + d + ", " + h + ", " + mn + ", " + s);
			
			cal.setTimeInMillis(cal.getTimeInMillis());
			
//			System.out.println("??? time in millis???" + cal.getTimeInMillis());
			
			return new Long(cal.getTimeInMillis());
		}
		
		return null;
	}
	
	public void setTime(int year, int month, int day, int hour, int minute, int second) throws UninitializedObject {
		Calendar cal = Calendar.getInstance();
		
		cal.set(Calendar.YEAR, year);
		cal.set(Calendar.MONTH, month);
		cal.set(Calendar.DAY_OF_MONTH, day);
		
		cal.set(Calendar.HOUR_OF_DAY, hour);
		cal.set(Calendar.MINUTE, minute);
		cal.set(Calendar.SECOND, second);
		cal.set(Calendar.MILLISECOND, 0);

		cal.setTimeInMillis(cal.getTimeInMillis());
		
		setTime(cal.getTimeInMillis());
	}
	
	private void setTime(long timeInMills) throws UninitializedObject {
		ts = (double)(timeInMills);
		
		time.setUnixTime((long)ts);
	}
	
	private void advanceTime() throws UninitializedObject {
		ts += advanceMilliseconds;
		
		time.setUnixTime((long)ts);
	}
	
	public boolean advanceSequence(boolean adjustTimeWhenDstChanges) throws UninitializedObject {
		transactionalListener.begin();
		
		if ((sequenceScript != null) && (sequenceIndex < sequenceScript.size())) {
			SequenceElementI t;
			do {
				t = sequenceScript.get(sequenceIndex);
				t.update();
				sequenceIndex = sequenceIndex + 1 + t.sequenceJumpPointer();
			} while ((sequenceIndex < sequenceScript.size()) && (t.isIntermediateStep()));
			
			// we're done
			if (sequenceIndex >= sequenceScript.size())
				sequenceScript = null;
		} else {
			if (advanceMilliseconds != null) {
	    		advanceTime();
			} else {
				if (calendarField <= 0)
	    			time.setCurrentTime();
	    		else
	    			time.addTime(1, calendarField);
			}
		}
			
		double tdstOffset = (double)(time.getTime().get(Calendar.DST_OFFSET) / 3600000.0);
		
		if ((adjustTimeWhenDstChanges) && (lastDstOffset != tdstOffset)) {
			time.addTime((long)((tdstOffset - lastDstOffset) * 3600)); 
		}
		
		transactionalListener.commit();
		
		boolean dstChanged = false;
		
		if (lastDstOffset != tdstOffset)
			dstChanged = true;
		
		lastDstOffset = tdstOffset;
		
		return dstChanged;
	}

	public int getCalendarField() {
		return calendarField;
	}

	public void setCalendarField(int calendarField) {
		this.calendarField = calendarField;
	}

	public ObserverLocation getMyLocation() {
		return myLocation;
	}

	public ObservationTime getTime() {
		return time;
	}

/*	public CelestialObject getSun() {
		return sun;
	} */

	public CelestialObject getMoon() {
		return moon;
	}
	
	public AbstractCelestialObject getSolarObjects() {
		return solarObjects;
	}
	
	public SolarSystemCoordinate[] getSolarSystemObjectCoordinates() {
		return ((SolarObjects)solarObjects).getSolarSystemObjectCoordinates();
	}	
	
	public List<CelestialObject> getStars() {
		return stars;
	}
	
	public void speedDown() {
		if (calendarField == Calendar.YEAR)
			calendarField = Calendar.MONTH;
		else if (calendarField == Calendar.MONTH)
			calendarField = Calendar.DAY_OF_MONTH;
		else if (calendarField == Calendar.DAY_OF_MONTH)
			calendarField = Calendar.HOUR_OF_DAY;
		else if (calendarField == Calendar.HOUR_OF_DAY)
			calendarField = Calendar.MINUTE;
		else if (calendarField == Calendar.MINUTE)
			calendarField = 0;
		else if (calendarField == 0)
			calendarField = -1;
	}
	
	public void speedUp() {
		if (calendarField < 0)
			calendarField = 0;
		else if (calendarField == 0)
			calendarField = Calendar.MINUTE;
		else if (calendarField == Calendar.MINUTE)
			calendarField = Calendar.HOUR_OF_DAY;
		else if (calendarField == Calendar.HOUR_OF_DAY)
			calendarField = Calendar.DAY_OF_MONTH;
		else if (calendarField == Calendar.DAY_OF_MONTH)
			calendarField = Calendar.MONTH;
		else if (calendarField == Calendar.MONTH)
			calendarField = Calendar.YEAR;
	}
	
	public void setSpeed(int speedIndex) {
		if (speedIndex == 0)
			calendarField = -1;
		else if (speedIndex == 1)
			calendarField = 0;
		else if (speedIndex == 2)
			calendarField = Calendar.MINUTE;
		else if (speedIndex == 3)
			calendarField = Calendar.HOUR_OF_DAY;
		else if (speedIndex == 4)
			calendarField = Calendar.DAY_OF_MONTH;
		else if (speedIndex == 5)
			calendarField = Calendar.MONTH;
		else if (speedIndex == 6)
			calendarField = Calendar.YEAR;
	}

	public int getCurrentSpeed() {
		if (calendarField == Calendar.YEAR)
			return 6;
		
		if (calendarField == Calendar.MONTH)
			return 5;
		
		if (calendarField == Calendar.DAY_OF_MONTH)
			return 4;
		
		if (calendarField == Calendar.HOUR_OF_DAY)
			return 3;
		
		if (calendarField == Calendar.MINUTE)
			return 2;
		
		if (calendarField == 0)
			return 1;
		
		return 0;
		
	}

	public int animateSpeedHint() {
		if (calendarField < 0)
			return 0;
		
		if (calendarField == 0)
			return 1;
		
		return 2;
	}
	
	public static String [] getSpeedLabels() {
		String [] s = new String[7];
		
		s[0] = "Step 1 fps (quartz clock)";
		s[1] = "Step 8 fps (pocket watch)";
		s[2] = "Advance by minutes";
		s[3] = "Advance by hours";
		s[4] = "Advance by days";
		s[5] = "Advance by months";
		s[6] = "Advance by years";
		return s;
	}
}
