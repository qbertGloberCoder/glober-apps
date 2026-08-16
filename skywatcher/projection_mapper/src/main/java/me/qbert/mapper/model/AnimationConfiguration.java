package me.qbert.mapper.model;

import java.util.ArrayList;
import java.util.Calendar;

import me.qbert.skywatch.astro.ObserverLocation;

public class AnimationConfiguration {
	private Calendar eventTime;
	private ObserverLocation eventLocation;
	private ObserverLocation observer;
	private long eventSecondsPerFrame;
	private ArrayList<SweepConfiguration> sweepConfigurations;
	public Calendar getEventTime() {
		return eventTime;
	}
	public void setEventTime(Calendar eventTime) {
		this.eventTime = eventTime;
	}
	public ObserverLocation getEventLocation() {
		return eventLocation;
	}
	public void setEventLocation(ObserverLocation eventLocation) {
		this.eventLocation = eventLocation;
	}
	public ObserverLocation getObserver() {
		return observer;
	}
	public void setObserver(ObserverLocation observer) {
		this.observer = observer;
	}
	public long getEventSecondsPerFrame() {
		return eventSecondsPerFrame;
	}
	public void setEventSecondsPerFrame(long eventSecondsPerFrame) {
		this.eventSecondsPerFrame = eventSecondsPerFrame;
	}
	public ArrayList<SweepConfiguration> getSweepConfigurations() {
		return sweepConfigurations;
	}
	public void setSweepConfigurations(ArrayList<SweepConfiguration> sweepConfigurations) {
		this.sweepConfigurations = sweepConfigurations;
	}
}
