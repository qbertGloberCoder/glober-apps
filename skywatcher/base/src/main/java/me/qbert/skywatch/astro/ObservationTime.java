package me.qbert.skywatch.astro;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.listeners.ObjectStateChangeListener;

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

public class ObservationTime {
	private Calendar time = null;
	
	private double julianDate;
	private double julianCentury;
	private double utcTime;
	private double timezoneAdjust;
	
	private int timeBiasMillis = 0;
	
	private ArrayList<ObjectStateChangeListener> listeners = new ArrayList<ObjectStateChangeListener>();
	
	public Calendar getTime() {
		return time;
	}

	public void setTime(Calendar time) throws UninitializedObject {
		this.time = time;
		recompute();
	}
	
	public void setCurrentTime() throws UninitializedObject {
		Calendar calendar = Calendar.getInstance();
		
		this.time.setTimeInMillis(calendar.getTimeInMillis() + (long)timeBiasMillis);
<<<<<<< HEAD
		recompute();
	}
	
	public void addTime(int units, int calendarClassField) throws UninitializedObject {
		this.time.add(calendarClassField, units);
=======
>>>>>>> 71e1364 (update pom version, add a time bias (or current time shift) to the time, add sun and moon precession calculators)
		recompute();
	}
	
	public void addTime(int units, int calendarClassField) throws UninitializedObject {
		this.time.add(calendarClassField, units);
		recompute();
	}
	
	public void addTime(int units, int calendarClassField) throws UninitializedObject {
		this.time.add(calendarClassField, units);
		recompute();
	}
	
	public void addTime(long seconds) throws UninitializedObject {
		this.time.setTimeInMillis(this.time.getTimeInMillis() + (seconds * 1000L));
		recompute();
	}
	
	public void setUnixTime(long unixTimestamp) throws UninitializedObject {
		this.time.setTimeInMillis(unixTimestamp);
		recompute();
	}
	
	public void setLocalTime(int year, int month, int day, int hour, int minute, int second) throws UninitializedObject {
		long lastUnixTime = time.getTimeInMillis()/1000;
		
		time.set(Calendar.YEAR, year);
		time.set(Calendar.MONTH, month - 1);
		time.set(Calendar.DAY_OF_MONTH, day);
		time.set(Calendar.HOUR_OF_DAY, hour);
		time.set(Calendar.MINUTE, minute);
		time.set(Calendar.SECOND, second);
		
		time.setTimeInMillis(time.getTimeInMillis());
		
		long newUnixTime = time.getTimeInMillis()/1000;
		if (lastUnixTime != newUnixTime) {
			julianDate = 0.0;
			recompute();
		}
	}

	public void initTime(TimeZone tz) throws UninitializedObject {
		this.time = Calendar.getInstance(tz, Locale.getDefault());
		recompute();
	}
	
	public double getJulianDate() {
		return julianDate;
	}

	public double getJulianCentury() {
		return julianCentury;
	}

	public double getUtcTime() {
		return utcTime;
	}

	public double getTimezoneAdjust() {
		return timezoneAdjust;
	}

	public void addListener(ObjectStateChangeListener stateChangeListener) {
		if (! listeners.contains(stateChangeListener))
			listeners.add(stateChangeListener);
	}
	
	public int getTimeBiasMillis() {
		return timeBiasMillis;
	}

	public void setTimeBiasMillis(int timeBiasMillis) {
		this.timeBiasMillis = timeBiasMillis;
	}

	private void recompute() throws UninitializedObject {
		if (time == null)
			throw new UninitializedObject();
		
		int year = time.get(Calendar.YEAR);
		int month = time.get(Calendar.MONTH) + 1;
		int day = time.get(Calendar.DAY_OF_MONTH);
		int hour = time.get(Calendar.HOUR_OF_DAY);
		int minute = time.get(Calendar.MINUTE);
		int second = time.get(Calendar.SECOND);
		double dstOffset = (double)(time.get(Calendar.DST_OFFSET) / 3600000.0);
		double tzOffset = (double)(time.get(Calendar.ZONE_OFFSET) / 3600000.0);
		timezoneAdjust = tzOffset + dstOffset;
		
		utcTime = (double)((hour*3600 + minute*60 + second)) / 86400.0;
		double newJulianDate = julianDay(day, month, year, timezoneAdjust) + utcTime;
		
		if (julianDate != newJulianDate) {
			julianDate = newJulianDate;
			julianCentury = (julianDate-2451545.0)/36525.0;
			
			notifyListeners();
		}
	}
	
	private void notifyListeners() {
		for (ObjectStateChangeListener listener : listeners) {
			listener.stateChanged(this, listener);
		}
	}

	private double julianDay (int date, int month, int year, double timezoneOffset)
	{
	    if (month<=2) {month=month+12; year=year-1;}
	    return (int)(365.25*year) + (int)(30.6001*(month+1)) - 15 + 1720996.5 + date - timezoneOffset/24.0;
	} 
}
