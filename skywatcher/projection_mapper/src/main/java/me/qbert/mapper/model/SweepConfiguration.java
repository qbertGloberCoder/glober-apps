package me.qbert.mapper.model;

public class SweepConfiguration {
	private PinInformation [] pins;
	private PinPing [] pinPingTimes;
	private ContourSweepLine contourSweepLine;
	public PinInformation[] getPins() {
		return pins;
	}
	public void setPins(PinInformation[] pins) {
		this.pins = pins;
	}
	public PinPing[] getPinPingTimes() {
		return pinPingTimes;
	}
	public void setPinPingTimes(PinPing[] pinPingTimes) {
		this.pinPingTimes = pinPingTimes;
	}
	public ContourSweepLine getContourSweepLine() {
		return contourSweepLine;
	}
	public void setContourSweepLine(ContourSweepLine contourSweepLine) {
		this.contourSweepLine = contourSweepLine;
	}
}
