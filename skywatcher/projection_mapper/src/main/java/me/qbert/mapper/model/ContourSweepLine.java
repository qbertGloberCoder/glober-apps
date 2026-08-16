package me.qbert.mapper.model;

import java.util.ArrayList;

public class ContourSweepLine {
	private ContourLine contourLine;
	private ArrayList<ContourLineSweepTiming> contourSweeps;
	public ContourLine getContourLine() {
		return contourLine;
	}
	public void setContourLine(ContourLine contourLine) {
		this.contourLine = contourLine;
	}
	public ArrayList<ContourLineSweepTiming> getContourSweeps() {
		return contourSweeps;
	}
	public void setContourSweeps(ArrayList<ContourLineSweepTiming> contourSweeps) {
		this.contourSweeps = contourSweeps;
	}
	
	public Double getPropagationDistanceForTime(long time, boolean pWave) {
		int nextTime = 0;

		int sweepsSize = contourSweeps.size();
		while (((nextTime < sweepsSize - 1) && (contourSweeps.get(nextTime + 1).getTime() < (double)(time))) ||
				((nextTime == sweepsSize - 1) && ((double)(time) >= contourSweeps.get(nextTime).getTime()))) {
			nextTime ++;
		}
		
		if (nextTime >= sweepsSize)
			return null;
		if (((double)(time) < contourSweeps.get(nextTime).getTime()) || ((nextTime == sweepsSize - 1) && ((double)(time) > contourSweeps.get(nextTime).getTime())))
			return null;
		
		double lastTimeSlot;
		double nextTimeSlot;
		double lastDistance;
		double nextDistance;
		
		if (nextTime < sweepsSize - 1) {
			lastTimeSlot = contourSweeps.get(nextTime).getTime();
			nextTimeSlot = contourSweeps.get(nextTime + 1).getTime();
			lastDistance = contourSweeps.get(nextTime).getDistanceKilometers();
			nextDistance = contourSweeps.get(nextTime + 1).getDistanceKilometers();
		} else {
			lastTimeSlot = contourSweeps.get(nextTime - 1).getTime();
			nextTimeSlot = contourSweeps.get(nextTime).getTime();
			lastDistance = contourSweeps.get(nextTime - 1).getDistanceKilometers();
			nextDistance = contourSweeps.get(nextTime).getDistanceKilometers();
		}
		
		double slope = (nextDistance - lastDistance)/(nextTimeSlot - lastTimeSlot);
		double kms = lastDistance + (slope * (((double)(time)-lastTimeSlot)));
		
		return kms;
	}
	
}
