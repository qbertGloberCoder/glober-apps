package me.qbert.foucault;

import javax.swing.SwingUtilities;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import me.qbert.foucault.listeners.PendulumStatisticsUpdateListener;
import me.qbert.foucault.model.PendulumStatistics;
import me.qbert.foucault.model.WeightPosition;
import me.qbert.foucault.physics.Pendulum;
import me.qbert.foucault.ui.Settings;

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

public class PendulumSceneFX extends Application implements PendulumStatisticsUpdateListener {
	static final int MAX_DOTS = 500;

	static final double bobSize = 0.2;
	static final double pointerLength = 0.1;
    static final double tableHeight = 1.0;
    static final double gap = 0.05;
    public static final double maxSwingDiameter = 3.4;

    int timeScale = 1;
    
    int lastUpdate = 0;
    
    boolean updateSimulator = false;
    double newSwingDiameter = maxSwingDiameter;
    double updateGravity = 9.80665;
    double updateRotation = 86164.09;
    double latitude = 1.0;
    double initialAzimuth = 185;
    boolean updateStableSwing = true;
    boolean updateDrag = false;
    double newDragCoefficient = 1.0;

	double pendulumLength = 150.0;
    double pivotHeight = pendulumLength + tableHeight + gap + bobSize + pointerLength;
    double bobX = 0;
    double bobZ = 0;
    double bobY = -pendulumLength;
    

    // Frame timing
    long lastFrame = 0;
    final long frameInterval = 1_000_000_000L / 30; // 30 FPS

    private Group pendulum;
    private Sphere pivot;
    private Sphere bobNode;
    private Cylinder rodNode;
    private Group inkLayer;
    
    PerspectiveCamera camera;
    Rotate rotateY;
    Rotate rotateX;

    final double cameraDistance = 7;
    double cameraAzimuth = -95;
    double cameraAltitude = -10;
    double fieldOfView = 50;
    boolean updateCameraPosition = false;
    
    Pendulum pendulumModel = new Pendulum();
    
    Settings settingsPanel = null;

    @Override
    public void start(Stage stage) {
    	pendulumModel.setLatitude(latitude);
    	pendulumModel.setStartPosition(maxSwingDiameter, initialAzimuth);
    	pendulumModel.setTargetFrameRate(30.0);
    	pendulumModel.setPendulumLength(pendulumLength);
    	pendulumModel.setStableSwing(updateStableSwing);
    	pendulumModel.setApplyDrag(updateDrag);
    	pendulumModel.setDragCoefficent(newDragCoefficient);
    	
    	pendulumModel.setRunning(true);
    	pendulumModel.setStatisticsUpdateListener(this);
    	
        Group root = new Group();

        // Flip Y so Y = up (POV-Ray style)
        root.getTransforms().add(new Scale(1, -1, 1));

        // =========================
        // FLOOR
        // =========================
        Box floor = new Box(60, 1, 60);
        floor.setTranslateY(-0.5);
        floor.setMaterial(new PhongMaterial(Color.color(0.2, 0.2, 0.7)));
        root.getChildren().add(floor);

        // =========================
        // WALLS
        // =========================
        PhongMaterial wallMat = new PhongMaterial(Color.LIGHTGRAY);

        Box wallN = new Box(21, pivotHeight + 3, 1);
        wallN.setTranslateZ(-10.5);
        wallN.setTranslateY((pivotHeight + 3) / 2 - 1);
        wallN.setMaterial(wallMat);

        Box wallS = new Box(21, pivotHeight + 3, 1);
        wallS.setTranslateZ(10.5);
        wallS.setTranslateY((pivotHeight + 3) / 2 - 1);
        wallS.setMaterial(wallMat);

        Box wallW = new Box(1, pivotHeight + 3, 21);
        wallW.setTranslateX(-10.5);
        wallW.setTranslateY((pivotHeight + 3) / 2 - 1);
        wallW.setMaterial(wallMat);

        Box wallE = new Box(1, pivotHeight + 3, 21);
        wallE.setTranslateX(10.5);
        wallE.setTranslateY((pivotHeight + 3) / 2 - 1);
        wallE.setMaterial(wallMat);

        root.getChildren().addAll(wallN, wallS, wallW, wallE);

        // =========================
        // COMPASS RING
        // =========================
        Cylinder compassRing = new Cylinder(maxSwingDiameter + 0.1, tableHeight);
        compassRing.setTranslateY(tableHeight/2);
        compassRing.setMaterial(new PhongMaterial(Color.GRAY));
        root.getChildren().add(compassRing);

        Image compassImage = new Image(
        	    getClass().getResource("/images/Brosen_windrose.svg.png").toExternalForm()
        	);

        Cylinder compassDisc = new Cylinder((maxSwingDiameter + 0.1) * 0.98, 0.01);
        compassDisc.setTranslateY(tableHeight + 0.001); // slightly above table

        PhongMaterial compassMat = new PhongMaterial();
        compassMat.setDiffuseMap(compassImage);
        compassMat.setSelfIlluminationMap(compassImage);
        compassMat.setSpecularColor(Color.TRANSPARENT);

        compassDisc.setMaterial(compassMat);
        compassDisc.setRotationAxis(Rotate.Y_AXIS);
        compassDisc.setRotate(180);        
        compassDisc.setScaleX(-1);
        
        root.getChildren().add(compassDisc);
        
        inkLayer = new Group();
        inkLayer.setTranslateY(tableHeight + 0.002);
        root.getChildren().add(inkLayer);
        
        // =========================
        // PENDULUM
        // =========================
        pendulum = new Group();

        // Pivot
        pivot = new Sphere(0.05);
        pivot.setMaterial(new PhongMaterial(Color.LIGHTGRAY));
        pivot.setTranslateY(pivotHeight);
        
        double rodMultiplier = (bobY - bobSize - pointerLength)/bobY;
        Cylinder rod = createCylinder(0.02, new Point3D(0, pivotHeight, 0), new Point3D(bobX * rodMultiplier, pivotHeight + (bobY - bobSize - pointerLength), bobZ * rodMultiplier));
        
        // Bob
        Sphere bob = new Sphere(bobSize);
        bob.setMaterial(new PhongMaterial(Color.RED));
        bob.setTranslateX(bobX);
        bob.setTranslateY(pivotHeight + bobY);
        bob.setTranslateZ(bobZ);

        pendulum.getChildren().addAll(pivot, rod, bob);
        root.getChildren().add(pendulum);
        
     // Make them fields or effectively final holders
        this.bobNode = bob;
        this.rodNode = rod;        

        // =========================
        // LIGHTS
        // =========================
        PointLight light1 = new PointLight(Color.WHITE);
        light1.setTranslateX(9);
        light1.setTranslateY(9);
        light1.setTranslateZ(-9);

        PointLight light2 = new PointLight(Color.color(0.4, 0.4, 0.4));
        light2.setTranslateX(-5);
        light2.setTranslateY(5);
        light2.setTranslateZ(-9);

        PointLight light3 = new PointLight(Color.color(0.6, 0.6, 0.6));
        light3.setTranslateX(1);
        light3.setTranslateY(8);
        light3.setTranslateZ(9);

        root.getChildren().addAll(light1, light2, light3);

        // =========================
        // CAMERA
        // =========================
     // 4. Define camera's initial position

        camera = new PerspectiveCamera(true);

        rotateY = new Rotate(0, Rotate.Y_AXIS);
        rotateX = new Rotate(0, Rotate.X_AXIS);
        camera.getTransforms().addAll(rotateY, rotateX);

        updateCameraPosition();

        camera.setFieldOfView(fieldOfView); 

        Scene scene = new Scene(root, 1920, 1080, true);
        scene.setCamera(camera);
        scene.setFill(Color.color(0.95, 0.95, 0.95));

        stage.setTitle("Foucault Pendulum");
        stage.setScene(scene);
        stage.show();
        
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastFrame < frameInterval) return;
                lastFrame = now;
                
                if (! pendulumModel.isRunning()) {
                	if (updateSimulator) {
                		while (inkLayer.getChildren().size() > 0) {
                            inkLayer.getChildren().remove(0);
                        }
                		
                		double newPivotHeight = pendulumLength + tableHeight + gap + bobSize + pointerLength;
                		if (pivot.getTranslateY() != newPivotHeight) {
                			double pivotDelta = newPivotHeight - pivot.getTranslateY();
                			
	                		for (Node node : pendulum.getChildren()) {
	                			node.setTranslateY(node.getTranslateY() + pivotDelta);
	                		}
                		}
                		
                		if (newSwingDiameter > maxSwingDiameter)
                			newSwingDiameter = maxSwingDiameter;
                    	pendulumModel.setLatitude(latitude);
                    	pendulumModel.setStartPosition(newSwingDiameter, initialAzimuth);
                    	pendulumModel.setPendulumLength(pendulumLength);
                    	pendulumModel.setGravity(updateGravity);
                    	pendulumModel.setPrecessionRate(updateRotation);
                    	pendulumModel.setStableSwing(updateStableSwing);
                    	pendulumModel.setApplyDrag(updateDrag);
                    	pendulumModel.setDragCoefficent(newDragCoefficient);
                    	pendulumModel.setRunning(true);
                    	
                    	updateSimulator = false;
                	}
                	return;
                }

                pivotHeight = pendulumLength + tableHeight + gap + bobSize + pointerLength;
                
                pendulumModel.stepToNextFrame();
                
                bobX = pendulumModel.getX();
                bobZ = pendulumModel.getY();
                bobY = pendulumModel.getZ();
                
                // Update bob position
                bobNode.setTranslateX(bobX);
                bobNode.setTranslateY(pivotHeight + bobY);
                bobNode.setTranslateZ(bobZ);

                // Update rod
                updateRod();
                
                if (updateCameraPosition) {
                	updateCameraPosition();
                    camera.setFieldOfView(fieldOfView); 
                	updateCameraPosition = false;
                }
                
                if (settingsPanel != null) {
                    PendulumStatistics stats = pendulumModel.getStatistics();
                    if (lastUpdate != (int)(stats.getSimulationSeconds())) {
	                	SwingUtilities.invokeLater(() -> {
	                		settingsPanel.updateStatistics(stats);
	                    	lastUpdate = (int)(stats.getSimulationSeconds());
		            	});
                    }
                }
            }
        };
        timer.start();

        
        SwingUtilities.invokeLater(() -> {
        	settingsPanel = new Settings(this);
        });        
        
        stage.setOnCloseRequest(event -> {
            System.exit(0);  // kills both JavaFX and Swing threads
        });
    }
    
    public void setTimeScale(int scaleValue) {
    	if (this.timeScale == scaleValue) 
    		return;
    	this.timeScale = scaleValue;
    	pendulumModel.setTargetFrameRate(30.0/(double)scaleValue);
    }
    
    public int getTimeScale() {
    	return this.timeScale;
    }
    
    public void setCameraAngle(int angle) {
    	if (cameraAzimuth == (double)angle)
    		return;
    	
    	cameraAzimuth = (double)angle;
    	updateCameraPosition = true;
    }
    
    public int getCameraAngle() {
    	return (int)cameraAzimuth;
    }
    
    public void setCameraAltitude(int altitude) {
    	if (cameraAltitude == (double)altitude)
    		return;
    	
    	cameraAltitude = (double)altitude;
    	updateCameraPosition = true;
    }
    
    public int getCameraAltitude() {
    	return (int)cameraAltitude;
    }
    
    public void setCameraFieldOfView(int fieldOfView) {
    	if (this.fieldOfView == (double)fieldOfView)
    		return;
    	
    	this.fieldOfView = (double)fieldOfView;
    	updateCameraPosition = true;
    }
    
    public void setLatitude(double latitude) {
    	if (this.latitude == latitude)
    		return;
    	
    	pendulumModel.setRunning(false);
    	
    	this.latitude = latitude;
    	updateSimulator = true;
    }
    
    public double getLatitude() {
    	return latitude;
    }
    
    public void setPendulumLength(double length) {
    	if (this.pendulumLength == length)
    		return;
    	
    	pendulumModel.setRunning(false);
    	
    	this.pendulumLength = length;
    	updateSimulator = true;
    }
    
    public double getPendulumLength() {
    	return pendulumLength;
    }
    
    public void setRotateSeconds(double seconds) {
    	if (this.updateRotation == seconds)
    		return;
    	
    	pendulumModel.setRunning(false);
    	updateRotation = seconds;
    	updateSimulator = true;
    }
    
    public double getRotateSeconds() {
    	return pendulumModel.getPrecessionRate();
    }
    
    public void setGravity(double gravity) {
    	if (this.updateGravity == gravity)
    		return;
    	
    	pendulumModel.setRunning(false);
    	updateGravity = gravity;
    	updateSimulator = true;
    }
    
    public double getGravity() {
    	return pendulumModel.getGravity();
    }
    
    public boolean isPrecessionActive() {
    	return pendulumModel.isPrecessionActive();
    }
    
    public void setPrecessionActive(boolean active) {
    	if (pendulumModel.isPrecessionActive() == active)
    		return;
    	
    	pendulumModel.setPrecessionActive(active);
    }
    
    public boolean isStableSwing() {
    	return updateStableSwing;
    }
    
    public void setStableSwing(boolean active) {
    	if (updateStableSwing == active)
    		return;
    	
    	pendulumModel.setRunning(false);
    	updateStableSwing = active;
    	updateSimulator = true;
    }
    
    public boolean isDrag() {
    	return updateDrag;
    }
    
    public void setDrag(boolean active) {
    	if (updateDrag == active)
    		return;
    	
    	pendulumModel.setRunning(false);
    	updateDrag = active;
    	updateSimulator = true;
    }
    
    public void setDragCoefficient(double coefficient) {
    	if (this.newDragCoefficient == coefficient)
    		return;
    	
    	pendulumModel.setRunning(false);
    	newDragCoefficient = coefficient;
    	updateSimulator = true;
    }
    
    public double getDragCoefficient() {
    	return newDragCoefficient;
    }
    
    public void setMaxSwingDiameter(double diameter) {
    	if (newSwingDiameter == diameter)
    		return;
    	
    	pendulumModel.setRunning(false);
    	newSwingDiameter = diameter;
    	updateSimulator = true;
    }
    
    public double getSwingDiameter() {
    	return newSwingDiameter;
    }
    
    public void setInitialAzimuth(double azimuth) {
    	if (this.initialAzimuth == azimuth)
    		return;
    	
    	pendulumModel.setRunning(false);
    	initialAzimuth = azimuth;
    	updateSimulator = true;
    }
    
    public double getInitialAzimuth() {
    	return initialAzimuth;
    }
    
	public double getMinTimeStep() {
		return pendulumModel.getMinTimeStep();
	}

	public void setMinTimeStep(double minTimeStep) {
		pendulumModel.setMinTimeStep(minTimeStep);
	}

	public double getMidTimeStep() {
		return pendulumModel.getMidTimeStep();
	}

	public void setMidTimeStep(double midTimeStep) {
		pendulumModel.setMidTimeStep(midTimeStep);
	}

	public double getMaxTimeStep() {
		return pendulumModel.getMaxTimeStep();
	}

	public void setMaxTimeStep(double maxTimeStep) {
		pendulumModel.setMaxTimeStep(maxTimeStep);
	}

	public boolean isStepMode() {
		return pendulumModel.isStepMode();
	}
	
	public void setStepMode(boolean stepMode) {
		pendulumModel.setStepMode(stepMode);
	}
	
	public boolean isRunning() {
		return pendulumModel.isRunning();
	}
	
	public void setRunning(boolean runMode) {
		pendulumModel.setRunning(runMode);
	}
	
    private void updateCameraPosition() {
        double cameraX = cameraDistance * Math.cos(Math.toRadians(cameraAltitude)) * Math.cos(Math.toRadians(cameraAzimuth));
        double cameraY = cameraDistance * Math.sin(Math.toRadians(cameraAltitude)) - tableHeight;
        double cameraZ = cameraDistance * Math.cos(Math.toRadians(cameraAltitude)) * Math.sin(Math.toRadians(cameraAzimuth));

        camera.setTranslateX(cameraX);
        camera.setTranslateY(cameraY);
        camera.setTranslateZ(cameraZ);
    	
        // Target coordinates
        double targetX = 0, targetY = -tableHeight, targetZ = 0;
        double camX = camera.getTranslateX();
        double camY = camera.getTranslateY();
        double camZ = camera.getTranslateZ();

        double dx = targetX - camX;
        double dy = targetY - camY;
        double dz = targetZ - camZ;

        // Calculate angles in degrees
        double angleY = Math.toDegrees(Math.atan2(dx, dz));
        double angleX = Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        
        // Apply to camera
        rotateY.setAngle(angleY);
        rotateX.setAngle(angleX);
    }
    
    private Cylinder createCylinder(double radius, Point3D origin, Point3D target) {
        Point3D yAxis = new Point3D(0, 1, 0);
        Point3D diff = target.subtract(origin);
        double height = diff.magnitude();

        Point3D mid = target.midpoint(origin);
        Translate moveToMidpoint = new Translate(mid.getX(), mid.getY(), mid.getZ());

        Point3D axisOfRotation = diff.crossProduct(yAxis);
        double angle = Math.acos(diff.normalize().dotProduct(yAxis));
        Rotate rotateAroundCenter = new Rotate(-Math.toDegrees(angle), axisOfRotation);

        Cylinder line = new Cylinder(radius, height);
        line.setMaterial(new PhongMaterial(Color.color(0.5, 0.5, 0.2)));

        line.getTransforms().addAll(moveToMidpoint, rotateAroundCenter);

        return line;
    }

    private void updateRod() {
        double rodMultiplier = (pendulumLength + bobSize + pointerLength)/pendulumLength;
        double alt = Math.atan2(bobY, Math.sqrt(bobX*bobX+bobZ*bobZ));
        double az = Math.atan2(bobZ, bobX);
        double length = Math.sqrt(bobX*bobX+bobY*bobY+bobZ*bobZ) * rodMultiplier;
        double endY = length * Math.sin(alt);
        double endX = length * Math.cos(alt) * Math.cos(az);
        double endZ = length * Math.cos(alt) * Math.sin(az);
        Cylinder newRod = createCylinder(0.02, new Point3D(0, pivotHeight, 0), new Point3D(endX, pivotHeight + endY, endZ));

        // Replace in scene graph
        Group parent = (Group) rodNode.getParent();
        int index = parent.getChildren().indexOf(rodNode);
        parent.getChildren().set(index, newRod);

        rodNode = newRod;
    }

	@Override
	public void statisticsUpdated(Object pendulumInstance, PendulumStatistics statistics) {
        Sphere dot = new Sphere(0.005, 4);
        dot.setMaterial(new PhongMaterial(Color.YELLOW));
        WeightPosition lastApex = statistics.getLastApexPosition();
        dot.setTranslateX(lastApex.getX());
        dot.setTranslateZ(lastApex.getY());
        dot.setTranslateY(0.002);
        inkLayer.getChildren().add(dot);
        
        if (inkLayer.getChildren().size() > MAX_DOTS) {
            inkLayer.getChildren().remove(0);
        }

        if (settingsPanel != null) {
            PendulumStatistics stats = pendulumModel.getStatistics();
        	SwingUtilities.invokeLater(() -> {
        		settingsPanel.updateStatistics(statistics);
            	lastUpdate = (int)(stats.getSimulationSeconds());
        	});
        }
	}

    public static void main(String[] args) {
        launch(args);
    }
}
