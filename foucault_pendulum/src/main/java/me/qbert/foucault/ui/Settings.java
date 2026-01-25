package me.qbert.foucault.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;

import javafx.application.Platform;
import me.qbert.foucault.PendulumSceneFX;
import me.qbert.foucault.model.CelestialObjectProfile;
import me.qbert.foucault.model.PendulumStatistics;
import me.qbert.foucault.model.SwingVector;
import me.qbert.foucault.service.CelestialObjectService;

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

public class Settings extends JFrame {
	private static final long serialVersionUID = 8900058531062027137L;
	
	private PendulumSceneFX mainClass;
	
	// Reference data
	private List<CelestialObjectProfile> celestialProfiles;
	
	// various controls
	private JComboBox<String> profileSelector = null;	
	private JSlider viewAngle = null;
	private JSlider viewAltitude = null;
	private JSlider fieldOfViewAngle = null;
	private JSlider timeScale = null;
	private JTextField latitudeField = null;
	private JTextField pendulumLengthField = null;
	private JTextField swingDiameterField = null;
	private JTextField initialAzimuth = null;
	private JCheckBox addCoriolisCheck = null;
	private JCheckBox stableSwingCheck = null;
	private JTextField dragCoefficientField = null;
	private JTextField rotateSecondsField = null;
	private JTextField gravityField = null;

	
	private JCheckBox dragCheck;

	private JLabel precessionRate;
	private JLabel pendulumTime;
	private JLabel computedLatitude;
	private JLabel forwardApex;
	private JLabel forwardNadir;
	private JLabel returnApex;
	private JLabel returnNadir;
	private JLabel swingCorrection;
	private JLabel simulationTime;
    

	public Settings(PendulumSceneFX mainClass) {
		super("Pendulum Controls");
		this.mainClass = mainClass;
		celestialProfiles = CelestialObjectService.getObjectProfiles();
		createControlPanel();
	}
	
    private void createControlPanel() {
        setLayout(new java.awt.GridBagLayout());
        GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;

        // NOTE: This is a trick to get the FIRST gridy to be 0, but all others to be one more than the previous...
        // In case we want to rearrange the order of the widgets
        c.gridy = -1;
        
        // Create control panel objects that the user interacts with
        createInputs(c);
        // then create the objects that provide information only
        createDisplayLabels(c);
        
        // disable fields that depend on the state of other fields
        dragCoefficientField.setEnabled(dragCheck.isSelected());
        
        // Make sure closing the Swing frame also exits JavaFX
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                Platform.exit();  // cleanly shuts down JavaFX
            }
        });
        
        pack();
        setMinimumSize(new Dimension(800, 450));
        setVisible(true);
    }
    
    // main create methods
    private void createInputs(GridBagConstraints c) {
        initProfileSelector(c);
        
        int startAngle = ((630 - mainClass.getCameraAngle()) % 360) - 180;
        viewAngle = createSlider("Camera Azimuth", c, -180, 180, startAngle);
        viewAngle.addChangeListener(e -> { setViewAzimuth(); });

        viewAltitude = createSlider("Camera Altitude", c, -90, 0, mainClass.getCameraAltitude());
        viewAltitude.addChangeListener(e -> { setViewAltitude(); });

        fieldOfViewAngle = createSlider("Field of view", c, 10, 70, 30);
        fieldOfViewAngle.addChangeListener(e -> { setFieldOfView(); });
        
        timeScale = createSlider("Time Scale", c, 1, 60, mainClass.getTimeScale());
        timeScale.addChangeListener(e -> { setTimeScale(); });

        latitudeField = createTextField("Latitude", c, Double.toString(mainClass.getLatitude()));
        latitudeField.addActionListener(e -> { setLatitude(); });
        latitudeField.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setLatitude();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
			}
		});

        pendulumLengthField = createTextField("Pendulum Length", c, Double.toString(mainClass.getPendulumLength()));
        pendulumLengthField.addActionListener(e -> {
        	setPendulumLength();
        });
        pendulumLengthField.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setPendulumLength();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
			}
		});
        
        swingDiameterField = createTextField("Swing diameter", c, Double.toString(mainClass.getSwingDiameter()));
        swingDiameterField.addActionListener(e -> {
        	setSwingDiameter();
        });
        swingDiameterField.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setSwingDiameter();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
			}
		});

        
        initialAzimuth = createTextField("Pendulum initial azimuth", c, Double.toString(mainClass.getInitialAzimuth()));
        initialAzimuth.addActionListener(e -> {
        	setInitialAzimuth();
        });
        initialAzimuth.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setInitialAzimuth();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
			}
		});
        
        addCoriolisCheck = createCheckBox("Add Coriolis", c, mainClass.isPrecessionActive());
        addCoriolisCheck.addActionListener(e -> {
        	setUseCoriolis();
        });
        
        stableSwingCheck = createCheckBox("Use a stable swing", c, mainClass.isStableSwing());
        stableSwingCheck.addActionListener(e -> {
        	setUseStableSwing();
        });
        
        dragCheck = createCheckBox("Use a drag coefficient", c, mainClass.isDrag());
        dragCheck.addActionListener(e -> {
        	setUseDragCoefficient();
        });

        dragCoefficientField = createTextField("Drag Coefficient", c, Double.toString(mainClass.getDragCoefficient()));
        dragCoefficientField.addActionListener(e -> {
        	setDragCoefficient();
        });
        dragCoefficientField.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setDragCoefficient();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
			}
		});
        
        rotateSecondsField = createTextField("Seconds per planetary Rotation", c, Double.toString(mainClass.getRotateSeconds()));
        rotateSecondsField.addActionListener(e -> {
        	setRotationPeriod();
        });
        rotateSecondsField.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setRotationPeriod();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
			}
		});
        
        gravityField = createTextField("Gravity", c, Double.toString(mainClass.getGravity()));
        gravityField.addActionListener(e -> {
        	setGravity();
        });
        gravityField.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setGravity();
			}
			
			@Override
			public void focusGained(FocusEvent e) {
			}
		});
    }
    
    private void createDisplayLabels(GridBagConstraints c) {
        precessionRate = createInfoBox("Precession rate", c);
        pendulumTime = createInfoBox("Time to complete 360°", c);
        computedLatitude = createInfoBox("Computed latitude", c);
        forwardApex = createInfoBox("Forward apex radius/azimuth", c);
        returnNadir = createInfoBox("Return nadir radius/azimuth", c);
        returnApex = createInfoBox("Return apex radius/azimuth", c);
        forwardNadir = createInfoBox("Forward nadir radius/azimuth", c);
        swingCorrection = createInfoBox("Swing stability coefficient", c);
        simulationTime = createInfoBox("Simulation time", c);
    }
    
    // Various common widget create methods
    private void initProfileSelector(GridBagConstraints c) {
        String [] profileNames = new String[1 + celestialProfiles.size()];
        int idx = 0;
        profileNames[idx ++] = "(custom)";
        for (CelestialObjectProfile profile : celestialProfiles)
        	profileNames[idx ++] = profile.getProfileName();
        profileSelector = createComboBox("Profile", c, profileNames, 1);
        profileSelector.addActionListener(e -> {
        	setProfile();
        });
    }

    private JComboBox<String> createComboBox(String label, GridBagConstraints c, String [] profileNames, int currentSelection) {
        c.gridx = 0;
        c.gridy ++;
        c.weightx = 0;
        add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        JComboBox<String> profileSelector = new JComboBox<String>(profileNames);
        if ((profileNames.length > 1) && (currentSelection >= 0) && (currentSelection < profileNames.length))
        	profileSelector.setSelectedIndex(currentSelection);
        add(profileSelector, c);
        
        return profileSelector;
    }

    private JSlider createSlider(String label, GridBagConstraints c, int min, int max, int startValue) {
        c.gridx = 0;
        c.weightx = 0;
        c.gridy ++;
        add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        JSlider slider = new JSlider(min, max, startValue);
        add(slider, c);
        
        return slider;
    }

    
    private JTextField createTextField(String label, GridBagConstraints c, String initialValue) {
        c.gridx = 0;
        c.weightx = 0;
        c.gridy++;
        add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        JTextField textField = new JTextField();
        textField.setText(initialValue);
        add(textField, c);

        return textField;
    }
    
    private JCheckBox createCheckBox(String checkboxLabel, GridBagConstraints c, boolean currentSelection) {
        c.gridx = 0;
        c.weightx = 0;
        c.gridy++;
        add(new JLabel(checkboxLabel), c);
        c.gridx = 1;
        c.weightx = 1;
        JCheckBox checkbox = new JCheckBox();
        checkbox.setSelected(currentSelection);
        add(checkbox, c);
    	
        return checkbox;
    }

    private JLabel createInfoBox(String label, java.awt.GridBagConstraints c) {
        c.gridx = 0;
        c.weightx = 0;
        c.gridy++;
        add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        JLabel labelBox = new JLabel();
        add(labelBox, c);
    	
        return labelBox;
    }

    // The various settings update routines
    private void setProfile() {
    	int selectIndex = profileSelector.getSelectedIndex();
    	if (selectIndex > 0) {
    		selectIndex --;

    		CelestialObjectProfile profile = celestialProfiles.get(selectIndex);
    		
    		rotateSecondsField.setText(Double.toString(profile.getSiderealRotateSeconds()));
			mainClass.setRotateSeconds(profile.getSiderealRotateSeconds());
    		gravityField.setText(Double.toString(profile.getAcceleration()));
    		mainClass.setGravity(profile.getAcceleration());
    		
    		if (profile.isWithLocation()) {
    			mainClass.setPendulumLength(profile.getPendulumLength());
    			pendulumLengthField.setText(Double.toString(profile.getPendulumLength()));
    			mainClass.setMaxSwingDiameter(profile.getSwingRadius());
    			swingDiameterField.setText(Double.toString(profile.getSwingRadius()));
    			mainClass.setLatitude(profile.getLatitude());
    	        latitudeField.setText(Double.toString(profile.getLatitude()));
    			
    		}
    	}
    }
    
    private void setViewAzimuth() {
    	mainClass.setCameraAngle(90 - viewAngle.getValue());
    }
    
    private void setViewAltitude() {
    	mainClass.setCameraAltitude(viewAltitude.getValue());
    }
    
    private void setFieldOfView() {
    	mainClass.setCameraFieldOfView((60 - fieldOfViewAngle.getValue()) + 20);
    }
    
    private void setTimeScale() {
    	mainClass.setTimeScale(timeScale.getValue());
    }

    private void setLatitude() {
    	try {
    		Double lat = Double.valueOf(latitudeField.getText());
    		if ((lat >= -90.0) && (lat <= 90.0))
    			mainClass.setLatitude(lat.doubleValue());
    		else
    	        latitudeField.setText(Double.toString(mainClass.getLatitude()));
   			profileSelector.setSelectedIndex(0);
    	} catch (Exception ex) {
            latitudeField.setText(Double.toString(mainClass.getLatitude()));
    	}
    }

    private void setPendulumLength() {
    	try {
    		Double length = Double.valueOf(pendulumLengthField.getText());
    		if ((length > mainClass.getSwingDiameter() + 0.01) && (length <= 900.0))
    			mainClass.setPendulumLength(length.doubleValue());
    		else
    			pendulumLengthField.setText(Double.toString(mainClass.getPendulumLength()));
			profileSelector.setSelectedIndex(0);
    	} catch (Exception ex) {
    		pendulumLengthField.setText(Double.toString(mainClass.getPendulumLength()));
    	}
    }
    
    private void setSwingDiameter() {
    	try {
    		Double length = Double.valueOf(swingDiameterField.getText());
    		if ((length >= 0.01) && (length <= PendulumSceneFX.maxSwingDiameter) && (length < mainClass.getPendulumLength() - 0.01))
    			mainClass.setMaxSwingDiameter(length.doubleValue());
    		else
    			swingDiameterField.setText(Double.toString(mainClass.getSwingDiameter()));
			profileSelector.setSelectedIndex(0);
    	} catch (Exception ex) {
    		swingDiameterField.setText(Double.toString(mainClass.getSwingDiameter()));
    	}
    }
    
    private void setInitialAzimuth() {
    	try {
    		Double azimuth = Double.valueOf(initialAzimuth.getText());
   			mainClass.setInitialAzimuth((360.0 + azimuth) % 360.0);
    	} catch (Exception ex) {
    		initialAzimuth.setText(Double.toString(mainClass.getInitialAzimuth()));
    	}
    }
    
    private void setUseCoriolis() {
    	mainClass.setPrecessionActive(addCoriolisCheck.isSelected());
    }
    
    private void setUseStableSwing() {
    	mainClass.setStableSwing(stableSwingCheck.isSelected());
    }
    
    private void setUseDragCoefficient() {
    	mainClass.setDrag(dragCheck.isSelected());
    	dragCoefficientField.setEnabled(dragCheck.isSelected());
    }
    
    private void setDragCoefficient() {
    	try {
    		Double coefficient = Double.valueOf(dragCoefficientField.getText());
    		if ((coefficient > 0.25) && (coefficient <= 1.0))
    			mainClass.setDragCoefficient(coefficient.doubleValue());
    		else
    			dragCoefficientField.setText(Double.toString(mainClass.getDragCoefficient()));
    			
    	} catch (Exception ex) {
    		dragCoefficientField.setText(Double.toString(mainClass.getDragCoefficient()));
    	}
    }
    
    private void setRotationPeriod() {
    	try {
    		Double seconds = Double.valueOf(rotateSecondsField.getText());
    		if ((seconds >= -50000000.0) && (seconds <= 50000000.0)) {
    			mainClass.setRotateSeconds(seconds.doubleValue());
    			profileSelector.setSelectedIndex(0);
    		}
    		else
    			rotateSecondsField.setText(Double.toString(mainClass.getRotateSeconds()));
    			
    	} catch (Exception ex) {
    		rotateSecondsField.setText(Double.toString(mainClass.getRotateSeconds()));
    	}
    }
    
    private void setGravity() {
    	try {
    		Double gravity = Double.valueOf(gravityField.getText());
    		if ((gravity >= 0.05) && (gravity <= 10000.0)) {
    			mainClass.setGravity(gravity.doubleValue());
       			profileSelector.setSelectedIndex(0);
    		}
    		else
    			gravityField.setText(Double.toString(mainClass.getGravity()));
    			
    	} catch (Exception ex) {
    		gravityField.setText(Double.toString(mainClass.getGravity()));
    	}
    }

    private static final double HOURS_PER_DAY   = 24.0;
    private static final double DAYS_PER_YEAR   = 365.2425;
    private static final double DAYS_PER_MONTH  = 30.436875;

    private String formatPrecessionTime(double hoursTotal) {
    	double initialHours = hoursTotal;
    	
        long years  = (long)(hoursTotal / (DAYS_PER_YEAR * HOURS_PER_DAY));
        hoursTotal -= years * DAYS_PER_YEAR * HOURS_PER_DAY;

        long months = (long)(hoursTotal / (DAYS_PER_MONTH * HOURS_PER_DAY));
        hoursTotal -= months * DAYS_PER_MONTH * HOURS_PER_DAY;

        long days   = (long)(hoursTotal / HOURS_PER_DAY);
        hoursTotal -= days * HOURS_PER_DAY;

        // Round remaining hours to 2 decimals
        hoursTotal = Math.round(hoursTotal * 100.0) / 100.0;

        StringBuilder sb = new StringBuilder();
        
        if (initialHours >= 24.0) {
        	sb.append(Math.round(initialHours * 1000.0) / 1000.0);
        	if (initialHours == 1.0)
        		sb.append(" hour, or ");
        	else
        		sb.append(" hours, or ");
        }

        if (years  > 0) sb.append(years).append(years == 1 ? " year, " : " years, ");
        if (months > 0) sb.append(months).append(months == 1 ? " month, " : " months, ");
        if (days   > 0) sb.append(days).append(days == 1 ? " day, " : " days, ");

        sb.append(hoursTotal).append(hoursTotal == 1.0 ? " hour" : " hours");

        return sb.toString();
    }

	public void updateStatistics(PendulumStatistics statistics) {
		double precession = statistics.getPrecessionRate();
		
		if (precessionRate != null) {
			double precessionDegrees = Math.toDegrees(precession) * 3600.0;
			precessionRate.setText(Double.toString(precessionDegrees) + " (degrees/hour)");
		}
		
		if (pendulumTime != null) {
			if (precession != 0.0) {
				double pendulumHoursSigned = 360.0 / (Math.toDegrees(statistics.getPrecessionRate()) * 3600.0);
				String pts = formatPrecessionTime(Math.abs(pendulumHoursSigned));
				pendulumTime.setText(pts);
				if ((computedLatitude != null) && (Math.abs(pendulumHoursSigned) >= statistics.getSiderealTime())) {
					double compLat = -Math.toDegrees(Math.asin(statistics.getSiderealTime() / pendulumHoursSigned));
					computedLatitude.setText("-1 * asin(" + statistics.getSiderealTime() + "/" + pendulumHoursSigned + ") = " + compLat);
				} else {
					computedLatitude.setText("-");
				}
			} else {
				pendulumTime.setText("-");
				computedLatitude.setText("-");
			}
		}

		SwingVector swingVector = statistics.getForwardApex();
		if (forwardApex != null) {
			double number = swingVector.getRadius();
			double azimuth = Math.toDegrees(swingVector.getAzimuth());
			azimuth = ((450 - azimuth) % 360);
			forwardApex.setText(Double.toString(number) + ", " + Double.toString(azimuth));
		}

		swingVector = statistics.getForwardNadir();
		if (forwardNadir != null) {
			double number = swingVector.getRadius();
			double azimuth = Math.toDegrees(swingVector.getAzimuth());
			azimuth = ((450 - azimuth) % 360);
			if (number == 0.0)
				forwardNadir.setText(Double.toString(number) + "/-");
			else
				forwardNadir.setText(Double.toString(number) + "/" + Double.toString(azimuth));
		}

		swingVector = statistics.getReturnApex();
		if (returnApex != null) {
			double number = swingVector.getRadius();
			double azimuth = Math.toDegrees(swingVector.getAzimuth());
			azimuth = ((450 - azimuth) % 360);
			returnApex.setText(Double.toString(number) + ", " + Double.toString(azimuth));
		}

		swingVector = statistics.getReturnNadir();
		if (returnNadir != null) {
			double number = swingVector.getRadius();
			double azimuth = Math.toDegrees(swingVector.getAzimuth());
			azimuth = ((450 - azimuth) % 360);
			if (number == 0.0)
				returnNadir.setText(Double.toString(number) + "/-");
			else
				returnNadir.setText(Double.toString(number) + "/" + Double.toString(azimuth));
		}

		if (swingCorrection != null) {
			double number = statistics.getSwingCorrection();
			swingCorrection.setText(Double.toString(number));
		}
		
		if (simulationTime != null) {
			int seconds = (int)(statistics.getSimulationSeconds());
			int milliseconds = (int)(1000.0 * (statistics.getSimulationSeconds() - (double)seconds));
			int hours = (int)(seconds / 3600);
			seconds -= (hours*3600);
			int minutes = (int)(seconds / 60);
			seconds -= (minutes*60);
			int timeScale = mainClass.getTimeScale();
			if (timeScale > 1)
				simulationTime.setText(String.format("%02d:%02d:%02d (%dx speed)", hours, minutes, seconds, timeScale));
			else
				simulationTime.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
		}
	}
}
