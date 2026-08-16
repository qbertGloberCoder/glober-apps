package me.qbert.foucault.physics;

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

import me.qbert.foucault.listeners.PendulumStatisticsUpdateListener;
import me.qbert.foucault.model.PendulumStatistics;

public class Pendulum {
	private static final double earthRotation = 23.0*3600.0 + 56*60.0 + 4.09;
	
	private Location location = new Location(40.0);
	private PrecessionRate precessionRate = new PrecessionRate(location, 2*Math.PI/earthRotation);

	private double pendulumLength = 150.0;

	private double desiredApexRadius = 2.5;
	private double desiredStartAngle = -90.0;
	private double desiredStartAngleRads = Math.toRadians(desiredStartAngle);
	private double returnAngle = desiredStartAngleRads;

	private boolean running = false;
	private boolean stepMode = true;
	
	private double x = 0.0;
	private double y = desiredApexRadius;
	private double z = -Math.sqrt(pendulumLength*pendulumLength - y*y);
	
	private double vx = 0.0;
	private double vy = 0.0;
	private double vz = 0.0;
	
	private double metersPerSecondSquared = 9.80665;
	private double gx = 0.0;
	private double gy = 0.0;
	private double gz = -metersPerSecondSquared;
	
	private double swingCorrectionSteps = 320000;
	private double minTimeStep = 0.000001;
	private double midTimeStep = 0.00001;
	private double maxTimeStep = 0.0001;
	private double timeStep = minTimeStep;
	private double animationTime = 0.0;
	private int frameCount = 0;
	private double targetFrameTime = 1/30.0;
	
	private boolean stableSwing = true;
	private boolean applyDrag = false;
	
	private double swingCorrection = 1.0;
	private double dragCoefficent = 1.0;
	private double lastDragTime = -1.0;
	private double lastSwingVelocity = 0.0;
	
	private boolean returnApex = true;
	private double currentRadius = desiredStartAngleRads;
	private double lastForwardApexTime = -1;
	private double lastReturnApexTime = -1;
	private double lastForwardApexAngle = desiredStartAngleRads;
	private double lastReturnApexAngle = Math.PI + desiredStartAngleRads;
	private double lastNadirRadius = desiredStartAngleRads*2;
	private double lastNadirAngle = desiredStartAngleRads;
	
	private double apexRadius = desiredApexRadius;
	
	private PendulumStatisticsUpdateListener statisticsUpdateListener = null;
	
	private PendulumStatistics statistics = new PendulumStatistics();
	
	public Pendulum() {
		statistics.setSiderealTime(earthRotation/3600.0);
	}
	
	public void setLatitude(double latitude) {
		location.setLatitude(latitude);
		precessionRate.reinit();
		running = false;
	}
	
	public void setPrecessionRate(double secondsPerRotation) {
		statistics.setSiderealTime(secondsPerRotation / 3600.0);
		precessionRate.setOmega(2*Math.PI/ secondsPerRotation);
		running = false;
	}
	
	public double getPrecessionRate() {
		return 2*Math.PI/ precessionRate.getOmega();
	}
	
	public PendulumStatisticsUpdateListener getStatisticsUpdateListener() {
		return statisticsUpdateListener;
	}

	public void setStatisticsUpdateListener(PendulumStatisticsUpdateListener statisticsUpdateListener) {
		this.statisticsUpdateListener = statisticsUpdateListener;
	}
	
	public boolean isPrecessionActive() {
		return precessionRate.isPrecessionActive();
	}

	public void setPrecessionActive(boolean precessionActive) {
		precessionRate.setPrecessionActive(precessionActive);
	}

	public void setPendulumLength(double pendulumLength) {
		this.pendulumLength = pendulumLength;
		running = false;
		resetPendulum();
	}
	
	public void setStartPosition(double apexRadius, double startAzimuth) {
		this.desiredApexRadius = apexRadius;
		returnAngle = desiredStartAngle = (450.0 - startAzimuth) % 360.0;
		
		running = false;
		
		resetPendulum();
	}
	
	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
		statistics.setRunning(running);
	}

	public boolean isStepMode() {
		return stepMode;
	}

	public void setStepMode(boolean stepMode) {
		this.stepMode = stepMode;
	}

	public void setGravity(double metersPerSecondSquared) {
		this.metersPerSecondSquared = metersPerSecondSquared;
		gx = 0.0;
		gy = 0.0;
		gz = -metersPerSecondSquared;
	}

	public double getGravity() {
		return metersPerSecondSquared;
	}
	
	public boolean isStableSwing() {
		return stableSwing;
	}

	public void setStableSwing(boolean stableSwing) {
		this.stableSwing = stableSwing;
	}

	public boolean isApplyDrag() {
		return applyDrag;
	}

	public void setApplyDrag(boolean applyDrag) {
		this.applyDrag = applyDrag;
	}

	public double getDragCoefficent() {
		return dragCoefficent;
	}

	public void setDragCoefficent(double dragCoefficent) {
		if ((dragCoefficent > 0.25) && (dragCoefficent < 1.0))
			this.dragCoefficent = dragCoefficent;
		else
			applyDrag = false;
	}
	
	public double getMinTimeStep() {
		return minTimeStep;
	}

	public void setMinTimeStep(double minTimeStep) {
		this.minTimeStep = minTimeStep;
	}

	public double getMidTimeStep() {
		return midTimeStep;
	}

	public void setMidTimeStep(double midTimeStep) {
		this.midTimeStep = midTimeStep;
	}

	public double getMaxTimeStep() {
		return maxTimeStep;
	}

	public void setMaxTimeStep(double maxTimeStep) {
		this.maxTimeStep = maxTimeStep;
	}

	private void resetPendulum() {
		desiredStartAngleRads = Math.toRadians(desiredStartAngle);
		returnAngle = desiredStartAngleRads;
		
		apexRadius = desiredApexRadius;
		animationTime = 0;
		lastDragTime = -1;

		returnApex = true;
		currentRadius = desiredStartAngleRads;
		lastForwardApexTime = -1;
		lastReturnApexTime = -1;
		lastForwardApexAngle = desiredStartAngleRads;
		lastReturnApexAngle = Math.PI + desiredStartAngleRads;
		lastNadirRadius = desiredStartAngleRads*2;
		lastNadirAngle = desiredStartAngleRads;
		
		x = desiredApexRadius * Math.cos(Math.toRadians(desiredStartAngle));
		y = desiredApexRadius * Math.sin(Math.toRadians(desiredStartAngle));
		z = -Math.sqrt(pendulumLength*pendulumLength - x*x - y*y);
		 
		vx = vy = vz = 0.0;
	}

	public void setTargetFrameRate(double targetFrameRate) {
		this.targetFrameTime = 1.0/targetFrameRate;
	}
	
	private void computeTimeStep() {
		// MOST positions in the swing can get away with maxTimeStep integration intervals...
		// To provide a more robust set of statistics and avoid sampling errors, as the
		// pendulum approaches the nadir and the apex, switch to smaller time intervals.
		if (Math.abs(currentRadius) < 0.005){
			timeStep = minTimeStep;
		} else if ((currentRadius <= desiredApexRadius) && ((currentRadius / desiredApexRadius) > 0.99)) {
			if ((currentRadius / desiredApexRadius) > 0.999) {
				timeStep = minTimeStep;
			} else {
				timeStep = midTimeStep;
			}
		} else {
			timeStep = maxTimeStep;
		}
	}

	public void stepOnce() {
		computeTimeStep();
		stepToTime(animationTime + (timeStep / 2));
	}
	 
	public void stepToNextFrame() {
		frameCount = (int)(animationTime / targetFrameTime);
		stepToTime((double)(frameCount + 1) * targetFrameTime);
	}
	
	public void stepToTime(double time) {
		if (! running)
			return;
		
		double omegaX = precessionRate.getOmegaX();
		double omegaY = precessionRate.getOmegaY();
		double omegaZ = precessionRate.getOmegaZ();
		
		boolean precessionActive = precessionRate.isPrecessionActive();
		
		if (timeStep <= 0.0)
			return;
		
		while ((apexRadius > 0.0001) && (animationTime < time)) {
			/* compute the unit vector along the string */
			double realLengthSquared = x*x + y*y + z*z;
			
			double ax;
			double ay;
			double az;
			
			/* compute acceleration based on gravity and coriolis */
			if (precessionActive) {
				ax = gx - 2.0*(omegaY*vz - omegaZ*vy);
				ay = gy - 2.0*(omegaZ*vx - omegaX*vz);
				az = gz - 2.0*(omegaX*vy - omegaY*vx);
			} else {
				ax = gx;
				ay = gy;
				az = gz;
			}
			
			/* Apply Velocity-Verlet half step correction */
		    double vxHalf = vx + 0.5*ax*timeStep;
		    double vyHalf = vy + 0.5*ay*timeStep;
		    double vzHalf = vz + 0.5*az*timeStep;
		    
		    /* compute tentative position */
		    double xT = x + vxHalf*timeStep;
		    double yT = y + vyHalf*timeStep;
		    double zT = z + vzHalf*timeStep;
		    
		    /* compute RATTLE position constraint */
		    double rTSq = xT*xT + yT*yT + zT*zT;

		    double lambda = (pendulumLength*pendulumLength - rTSq) / (2.0 * realLengthSquared);

		    double xNew = xT + lambda*x;
		    double yNew = yT + lambda*y;
		    double zNew = zT + lambda*z;
		    
		    /* compute RATTLE velocity constraint */
		    double rNewSq = xNew*xNew + yNew*yNew + zNew*zNew;

		    double rDotV =
		        xNew*vxHalf +
		        yNew*vyHalf +
		        zNew*vzHalf;

		    double mu = -rDotV / rNewSq;

		    vxHalf += mu*xNew;
		    vyHalf += mu*yNew;
		    vzHalf += mu*zNew;
		    
		    /* recompute acceleration */
		    double rNewLen = Math.sqrt(rNewSq);

		    double rhatXn = xNew / rNewLen;
		    double rhatYn = yNew / rNewLen;
		    double rhatZn = zNew / rNewLen;

		    double gDotRnew = gx*rhatXn + gy*rhatYn + gz*rhatZn;

		    double gtxNew = gx - gDotRnew*rhatXn;
		    double gtyNew = gy - gDotRnew*rhatYn;
		    double gtzNew = gz - gDotRnew*rhatZn;

		    double axNew;
		    double ayNew;
		    double azNew;
		    
		    if (precessionActive) {
		    	axNew = gtxNew - 2.0*(omegaY*vzHalf - omegaZ*vyHalf);
		    	ayNew = gtyNew - 2.0*(omegaZ*vxHalf - omegaX*vzHalf);
		    	azNew = gtzNew - 2.0*(omegaX*vyHalf - omegaY*vxHalf);
		    } else {
		    	axNew = gtxNew;
		    	ayNew = gtyNew;
		    	azNew = gtzNew;
		    }
		    
		    /* complete the velocity calculation */
		    vx = vxHalf + 0.5*axNew*timeStep;
		    vy = vyHalf + 0.5*ayNew*timeStep;
		    vz = vzHalf + 0.5*azNew*timeStep;

		    /* update the pendulum position */
		    x = xNew;
		    y = yNew;
		    z = zNew;
		    
		    /* Apply a correction force to keep the swing stable
		     * 
		     * In the event correction is precisely 1.0, don't bother trying to muck
		     * with velocities, introducing possible rounding errors where none was
		     * needed
		     */
		    if ((stableSwing) && (swingCorrection != 1.0)) {
		    	vx *= swingCorrection;
				vy *= swingCorrection;
				vz *= swingCorrection;
		    }
		    
		    /* detect if we reached the apex yet */
		    currentRadius = Math.sqrt(x*x+y*y);
		    
		    double swingVelocity;
		    
		    if (currentRadius > 0.0)
		    	swingVelocity = (x*vx + y*vy)/currentRadius;
		    else
		    	swingVelocity = 0.0;
		    
		    double newAngle = Math.atan2(y,  x);
		    if ((lastSwingVelocity > 0.0) && (swingVelocity < 0.0)) {
		    	if (stableSwing) {
				    swingCorrection = (swingCorrection + (1.0+(((desiredApexRadius / currentRadius)-1.0)/swingCorrectionSteps))) / 2.0;
		    	}
		    	
		    	apexRadius = currentRadius;
		    	
			    double testAngle = Math.abs(newAngle - returnAngle);
		    	if (testAngle > Math.PI)
		    		testAngle -= (2*Math.PI);
		    	else if (testAngle < -Math.PI)
		    		testAngle += (2*Math.PI);
		    	
			    testAngle = Math.abs(testAngle);
			    
			    if (testAngle < 0.5) {
			    	returnApex = true;
			    	
			    	if (lastReturnApexTime >= 0.0) {
			    	    double dAz = newAngle - lastReturnApexAngle;

			    	    if (dAz > Math.PI)
			    	        dAz -= 2*Math.PI;
			    	    else if (dAz < -Math.PI)
			    	        dAz += 2*Math.PI;

			    	    double dt = animationTime - lastReturnApexTime;

			    	    double precessionRate = dAz / dt;
			    	    reportPrecessionRate(precessionRate, returnApex);
			    	}

			    	lastReturnApexTime = animationTime;
			    	lastReturnApexAngle = newAngle;
			    	returnAngle = newAngle;
			    } else {
			    	returnApex = false;
			    	if (lastForwardApexTime >= 0.0) {
			    	    double dAz = newAngle - lastForwardApexAngle;

			    	    if (dAz > Math.PI)
			    	        dAz -= 2*Math.PI;
			    	    else if (dAz < -Math.PI)
			    	        dAz += 2*Math.PI;

			    	    double dt = animationTime - lastForwardApexTime;

			    	    double precessionRate = dAz / dt;
			    	    reportPrecessionRate(precessionRate, returnApex);
			    	}
			    	lastForwardApexTime = animationTime;
			    	lastForwardApexAngle = newAngle;
			    }
			    
			    apexDetected(newAngle, returnApex);
		    } else if ((lastSwingVelocity < 0.0) && (swingVelocity > 0.0)) {
			    nadirDetected(lastNadirRadius, lastNadirAngle, returnApex);
			    lastNadirRadius = Double.POSITIVE_INFINITY;
		    } else {
		    	if (currentRadius < lastNadirRadius) {
		    	    lastNadirRadius = currentRadius;
		    	    lastNadirAngle = newAngle;
		    	}
		    }
		    
		    if (swingVelocity != 0.0)
		    	lastSwingVelocity = swingVelocity;

		    if ((int)(time/2) > (int)(lastDragTime/2)) {
		        if ((stableSwing) && (applyDrag) && (dragCoefficent < 1.0)) {
		        	desiredApexRadius *= dragCoefficent;
		    	}
		        lastDragTime = time;
		    }

		    animationTime += timeStep;

			computeTimeStep();
		}
		
		statistics.setSimulationSeconds(animationTime);
	}
	
	public double getX() {
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	public double getZ() {
		return z;
	}
	
	public PendulumStatistics getStatistics() {
		return statistics.copy();
	}
	
	protected void apexDetected(double angle, boolean returnApex) {
		running = !stepMode;
		statistics.setRunning(running);
		
		statistics.getLastApexPosition().updatePosition(x,y,z);
		if (returnApex)
			statistics.setReturnApex(apexRadius, angle);
		else
			statistics.setForwardApex(apexRadius, angle);
		
		statistics.setSwingCorrection(swingCorrection);
		statistics.setSimulationSeconds(animationTime);
		
		if (statisticsUpdateListener != null)
			statisticsUpdateListener.statisticsUpdated(this, statistics.copy());
//		System.out.println("?? " + x + ", " + y + ", " + z);
	}
	
	protected void nadirDetected(double radius, double angle, boolean forwardNadir) {
		running = !stepMode;
		statistics.setRunning(running);
		
		if (forwardNadir)
			statistics.setForwardNadir(radius, angle);
		else
			statistics.setReturnNadir(radius, angle);
		
		statistics.setSimulationSeconds(animationTime);
		
		if (statisticsUpdateListener != null)
			statisticsUpdateListener.statisticsUpdated(this, statistics.copy());

/*		if (forwardNadir) {
			System.out.println(
					"For.Ap. = " + statistics.getForwardApex().getRadius() + "/" + statistics.getForwardApex().getAzimuth() +
					" Ret.Nad. = " + statistics.getReturnNadir().getRadius() + "/" + statistics.getReturnNadir().getAzimuth() +
					" Ret.Ap. = " + statistics.getReturnApex().getRadius() + "/" + statistics.getReturnApex().getAzimuth() +
					" For.Nad. = " + statistics.getForwardNadir().getRadius() + "/" + statistics.getForwardNadir().getAzimuth() +
					" Prec. Rate. = " + statistics.getPrecessionRate() +
					" Nad. pos[x/y/z] = " + x + "/" + y + "/" + z +
					" Nad. vel[x/y/z] = " + vx + "/" + vy + "/" + vz
					);
		} */
//		System.out.println("?? " + x + ", " + y + ", " + z);
	}
	
	protected void reportPrecessionRate(double precessionRate, boolean returnApex) {
		statistics.setPrecessionRate(precessionRate);
		
/*		if (returnApex)
			System.out.println("PrR: " + precessionRate);
		else
			System.out.println("PrF: " + precessionRate); */
//		System.out.println("?? RATE? " + Math.toDegrees(precessionRate));
	}
}
