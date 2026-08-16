package me.qbert.mapper.model;

public class PinPing {
	private double time;
	private PinInformation pin;
	private int pingOversize;
	public double getTime() {
		return time;
	}
	public void setTime(double time) {
		this.time = time;
	}
	public PinInformation getPin() {
		return pin;
	}
	public void setPin(PinInformation pin) {
		this.pin = pin;
	}
	public int getPingOversize() {
		return pingOversize;
	}
	public void setPingOversize(int pingOversize) {
		this.pingOversize = pingOversize;
	}
}
