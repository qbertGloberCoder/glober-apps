package me.qbert.skywatch.camera;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
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
import me.qbert.skywatch.camera.catalog.StarCatalogLoader;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.listeners.ObjectStateChangeListener;
import me.qbert.skywatch.model.CelestialAddress;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

public class TestDifferentStarCases implements ObjectStateChangeListener {
	private ObserverLocation myLocation = new ObserverLocation();
	private TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
	private ObservationTime time = new ObservationTime();
	private CelestialObject moon;
	private AbstractCelestialObject solarObjects;
	private List<CelestialObject> stars;
	private int starLevel = 1;
	
	private ArrayList<CelestialObject> [] starObjects;

	ObjectDirectionRaDec lastSunDirection;
	ObjectDirectionRaDec lastMoonDirection;
	ObjectDirectionRaDec [] lastStarsDirections;
	
	private TestDifferentStarCases() {
		StarCatalogLoader starLoader = new StarCatalogLoader();
		
		try {
			FileInputStream fis = new FileInputStream(new File("src/main/resources/stars.db"));
			List<StarCoordinate> starCoordinates = starLoader.load(fis);
			
			starObjects = new ArrayList[3];
			for (int i = 0;i < starObjects.length;i ++) {
				starObjects[i] = new ArrayList<CelestialObject>();
			}
			
			time.initTime(TimeZone.getDefault());
			time.setLocalTime(2026, 8, 15, 13, 0, 0);
			
			time.initTime(TimeZone.getDefault());
			time.setCurrentTime();
			solarObjects = (AbstractCelestialObject) SolarObjects.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
			moon = MoonObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
			
			stars = new ArrayList<CelestialObject>();
			
			for (StarCoordinate star : starCoordinates) {
				CelestialAddress starAddress = new CelestialAddress();
				starAddress.setAddress(star.getRightAscension(), star.getDeclination());
				CelestialObject starObj = StarObject.create().setStarLocation(starAddress).setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
				transactionalListener.addListener(this);
				stars.add(starObj);
				starObjects[star.getGroupLevel() - 1].add(starObj);
			}
			
			transactionalListener.addListener(solarObjects);
			transactionalListener.addListener(moon);
			transactionalListener.addListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private void testInit() throws UninitializedObject {
		transactionalListener.begin();
		myLocation.setGeoLocation(45, -75);
		time.setLocalTime(2026, 8, 15, 12, 0, 0);
		transactionalListener.commit();
	}
	
	private void confirmProperObjectsNotified(int starLevel, boolean testTheTest) {
		System.out.println("Testing " + starLevel + " and testTheTest=" + testTheTest);
		try {
			if (testTheTest) { 
				this.starLevel = 2;
				// force to level 3 but make the transactional listener init to level 2
				starLevel = 3;
			} else {
				this.starLevel = starLevel;
			}
			
			testInit();
		}
		catch (Exception e) {
			e.printStackTrace();
			return;
		}

		// default it to the sun
		solarObjects.setObjectIndex(0);
		lastSunDirection = solarObjects.getCurrentDirection();
		lastMoonDirection = moon.getCurrentDirection();
		lastStarsDirections = new ObjectDirectionRaDec[starObjects.length];
		boolean [] starGroupShouldUpdate = new boolean[starObjects.length];
		for (int i = 0;i < starObjects.length;i ++) {
			if (starObjects[i] != null) {
				lastStarsDirections[i] = starObjects[i].get(0).getCurrentDirection();
			}
			starGroupShouldUpdate[i] = (i < starLevel) ? true : false;
		}
		
		for (int i = 0;i < 100;i ++) {
			boolean tryOnlyRetest = false;
			
			try {
				tickAndTest((i+1), 3200, starGroupShouldUpdate);
			}
			catch (Exception e) {
				e.printStackTrace();
				if (testTheTest) {
					System.out.println("Let's manually update for the stars for \"level 3\" and try again");
					this.starLevel = 3;
					notifyStateChange();
					testTheTest = false;
					for (int j = 0;j < starObjects.length;j ++) {
						starGroupShouldUpdate[j] = (j < starLevel) ? true : false;
						
						// force the next set of tests to pass
						lastSunDirection.setDeclination(lastSunDirection.getDeclination() + 0.001);
						lastMoonDirection.setDeclination(lastMoonDirection.getDeclination() + 0.001);
						if (j < starLevel)
							lastStarsDirections[j].setDeclination(lastStarsDirections[j].getDeclination() + 0.001);
					}
					tryOnlyRetest = true;
				}
				else
					return;
			}
			
			if (tryOnlyRetest) {
				try {
					testOnly((i+1), starGroupShouldUpdate);
				}
				catch (Exception e) {
					e.printStackTrace();
					return;
				}
			}
		}
		
		System.out.println("Test passed");
	}
	
	private void printIdentifierAndThrowException(String testIdentifier, double ra1, double ra2, double dec1, double dec2) throws Exception {
		System.out.println("ERROR: " + testIdentifier + ": " + ra1 + "!=" + ra2 + " or " + dec1 + "!=" + dec2);
		throw new Exception(testIdentifier + ": " + ra1 + "!=" + ra2 + " or " + dec1 + "!=" + dec2);
	}
	
	private void testUpdate(String testIdentifier, ObjectDirectionRaDec initDirection, ObjectDirectionRaDec newDirection, boolean expectedToChange) throws Exception {
		boolean didChange = false;
		if ((initDirection.getDeclination() != newDirection.getDeclination()) || (initDirection.getRightAscension() != newDirection.getRightAscension()))
				didChange = true;
		if ((didChange) && (! expectedToChange))
			printIdentifierAndThrowException(testIdentifier + " changed when it should not", initDirection.getRightAscension(), newDirection.getRightAscension(), 
						initDirection.getDeclination(), newDirection.getDeclination());
		if ((!didChange) && (expectedToChange))
			printIdentifierAndThrowException(testIdentifier + " did not change when it should", initDirection.getRightAscension(), newDirection.getRightAscension(), 
						initDirection.getDeclination(), newDirection.getDeclination());
	}
	
	private void testOnly(int testCount, boolean[] starGroupShouldUpdate) throws Exception {
		solarObjects.setObjectIndex(0);
		ObjectDirectionRaDec oldDirection;
		
		oldDirection = lastSunDirection;
		lastSunDirection = solarObjects.getCurrentDirection();
		testUpdate("SUN (" + testCount + ")", oldDirection, lastSunDirection, true);
		
		oldDirection = lastMoonDirection;
		lastMoonDirection = moon.getCurrentDirection();
		testUpdate("MOON (" + testCount + ")", oldDirection, lastMoonDirection, true);

		for (int i = 0;i < starObjects.length;i ++) {
			oldDirection = lastStarsDirections[i];
			lastStarsDirections[i] = starObjects[i].get(0).getCurrentDirection();
			testUpdate("Star group (" + testCount + ") " + (i + 1), oldDirection, lastStarsDirections[i], starGroupShouldUpdate[i]);
		}
	}

	private void tickAndTest(int testCount, long seconds, boolean[] starGroupShouldUpdate) throws Exception {
		transactionalListener.begin();
		time.addTime(seconds);
		transactionalListener.commit();

		testOnly(testCount, starGroupShouldUpdate);
	}


	private void notifyStateChange() {
		for (int i = 0;i < starLevel;i ++) {
			for (CelestialObject star : starObjects[i]) {
				star.stateChanged(this, star);
			}
		}
	}
	
	@Override
	public void stateChanged(Object source, ObjectStateChangeListener listener) {
		// Ignore "this" listener. This should also be registered through transactionalListener which would trigger only one stars state change
		
		notifyStateChange();
	}
	
	public static void main(String[] args) {
		TestDifferentStarCases test = new TestDifferentStarCases();
		
		test.confirmProperObjectsNotified(1, false);
		test.confirmProperObjectsNotified(2, false);
		test.confirmProperObjectsNotified(3, false);
		test.confirmProperObjectsNotified(1, true);
	}
}
