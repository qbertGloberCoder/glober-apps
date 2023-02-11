package me.qbert.skywatch.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.service.CelestialObjects;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.util.AnimationTimer;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ImageRenderer;
import me.qbert.ui.util.RenderComponentUtil;

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

public class MainFrame extends JFrame implements KeyListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2330068972046366353L;
	
	private JMenuBar menubar;
	
	private JCheckBoxMenuItem timerMenu;
	private JCheckBoxMenuItem railwayStyleMenu;
	private JCheckBoxMenuItem twentyFourHourStyleMenu;
	private JCheckBoxMenuItem zenithAnglesMenu;
	private JCheckBoxMenuItem [] speedSettings;
	
    private JCheckBoxMenuItem dayNightOffMenu;
    private JCheckBoxMenuItem dayNightPartialMenu;
    private JCheckBoxMenuItem dayNightFullMenu;

    private JCheckBoxMenuItem mickeyFaceMenu;
    
	private Canvas canvas;
	
	private boolean timerRunning = false;
    private Timer timer = null;
    private AnimationTimer animationTimer = null;
    
    private int timerInterval = 1000;
    
    private CelestialObjects celestialObjects;
    
    private boolean mickeyFace = true;
    
    private Properties props;
    private static GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0];
    private static File propsFile = new File("settings.properties");
	
	public MainFrame() {
		super("Multi-transformation earth clock");
		
        //Creating the MenuBar and adding components
        menubar = new JMenuBar();
        JMenu m1 = new JMenu("File");
        menubar.add(m1);
        JMenuItem m11 = new JMenuItem("Exit");
        m1.add(m11);

        JMenu m2 = new JMenu("Options");
        menubar.add(m2);
        JMenuItem fullscreenMenu = new JMenuItem("Full screen");
        m2.add(fullscreenMenu);

        timerMenu = new JCheckBoxMenuItem("Timer");
        m2.add(timerMenu);
        railwayStyleMenu = new JCheckBoxMenuItem("Swiss railway style");
        m2.add(railwayStyleMenu);
        
        twentyFourHourStyleMenu = new JCheckBoxMenuItem("24 hour style clock");
        m2.add(twentyFourHourStyleMenu);
        twentyFourHourStyleMenu.setSelected(true);
        
        zenithAnglesMenu = new JCheckBoxMenuItem("Zenith angles");
        m2.add(zenithAnglesMenu);
        JMenu speedMenu = new JMenu("day/nights opacity");
        m2.add(speedMenu);
        dayNightOffMenu = new JCheckBoxMenuItem("Off");
        speedMenu.add(dayNightOffMenu);
        dayNightPartialMenu = new JCheckBoxMenuItem("Partial");
        speedMenu.add(dayNightPartialMenu);
        dayNightFullMenu = new JCheckBoxMenuItem("Full");
        speedMenu.add(dayNightFullMenu);
        mickeyFaceMenu = new JCheckBoxMenuItem("Mickey Mouse face");
        m2.add(mickeyFaceMenu);
        
        mickeyFaceMenu.setSelected(true);

        JMenu m3 = new JMenu("Speed");
        menubar.add(m3);
        JMenuItem timerSpeedDown = new JMenuItem("Slow Down");
        m3.add(timerSpeedDown);
        JMenuItem timerSpeedUp = new JMenuItem("Speed Up");
        m3.add(timerSpeedUp);
        
        m3.add(new JSeparator());
        
        String [] speedLabels = CelestialObjects.getSpeedLabels();
        speedSettings = new JCheckBoxMenuItem[speedLabels.length];
        for (int i = 0;i < speedLabels.length;i ++) {
        	speedSettings[i] = new JCheckBoxMenuItem(speedLabels[i]);
        	m3.add(speedSettings[i]);
        	
        	final int index = i;
        	speedSettings[i].addActionListener(new ActionListener() {
    			@Override
    			public void actionPerformed(ActionEvent e) {
    				celestialObjects.setSpeed(index);
    				updateSpeedMenu(index);
    			}
    		});
        }
        

        JMenu m4 = new JMenu("Location");
        menubar.add(m4);
        JMenuItem latPicker = new JMenuItem("Latitude");
        m4.add(latPicker);
        JMenuItem lonPicker = new JMenuItem("Longitude");
        m4.add(lonPicker);
        
        m11.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});

        final Frame thisInstance = this;
        fullscreenMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				device.setFullScreenWindow(thisInstance);
				menubar.setVisible(false);
			}
		});
        
        timerMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleTimer();
			}
		});
        
        railwayStyleMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setRailwayStyleClock(! celestialObjects.isRailwayStyleClock());
				railwayStyleMenu.setSelected(celestialObjects.isRailwayStyleClock());
				redrawClock();
				setRailwayStyle(celestialObjects.isRailwayStyleClock());
			}
		});
        
        twentyFourHourStyleMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setTwentyFourHourClock(! celestialObjects.isTwentyFourHourClock());
				twentyFourHourStyleMenu.setSelected(celestialObjects.isTwentyFourHourClock());
				redrawClock();
				setTwentyFourHourStyle(celestialObjects.isTwentyFourHourClock());
			}
		});
        
        zenithAnglesMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setRenderContourLines(! celestialObjects.isRenderContourLines());
				zenithAnglesMenu.setSelected(celestialObjects.isRenderContourLines());
				redrawClock();
				setZenithAngles(celestialObjects.isRenderContourLines());
			}
		});
        
        mickeyFaceMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				mickeyFace = ! mickeyFace;
				mickeyFaceMenu.setSelected(mickeyFace);
				celestialObjects.setClockFace(mickeyFace);
				redrawClock();
				setMickeyFace(mickeyFace);
			}
		});
        
        dayNightOffMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setDayNightRendered(false);
				updateDayNightsMenu();
				redrawClock();
				setDayNightOpacity(false, celestialObjects.getDayNightFillLevel());
			}
		});
        
        dayNightPartialMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setDayNightRendered(true);
				celestialObjects.setDayNightFillLevel(224);
				updateDayNightsMenu();
				redrawClock();
				setDayNightOpacity(true, celestialObjects.getDayNightFillLevel());
			}
		});
        
        dayNightFullMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setDayNightRendered(true);
				celestialObjects.setDayNightFillLevel(255);
				updateDayNightsMenu();
				redrawClock();
				setDayNightOpacity(true, celestialObjects.getDayNightFillLevel());
			}
		});
        
        timerSpeedDown.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.speedDown();
				updateSpeedMenu();
			}
		});
        
        timerSpeedUp.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.speedUp();
				updateSpeedMenu();
			}
		});
        
        latPicker.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFrame f = new JFrame();   
			    String latitude = JOptionPane.showInputDialog(f, "Enter Latitude", getPropertiesLatitude().toString());
			    if (latitude != null) {
				    try {
				    	Double d = new Double(latitude);
				    	celestialObjects.setLatitude(d);
				    	setPropertiesLatitude(d);
				    } catch (NumberFormatException ex) {
				    }
			    }
				redrawClock();
			}
		});
        
        lonPicker.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFrame f = new JFrame();   
			    String longitude = JOptionPane.showInputDialog(f, "Enter Longitude", getPropertiesLongitude().toString());
			    if (longitude != null) {
				    try {
				    	Double d = new Double(longitude);
				    	celestialObjects.setLongitude(d);
				    	setPropertiesLongitude(d);
				    } catch (NumberFormatException ex) {
				    	
				    }
			    }
				redrawClock();
			}
		});

        canvas = new Canvas();
        
        //Adding Components to the frame.
        getContentPane().add(BorderLayout.NORTH, menubar);
        getContentPane().add(BorderLayout.CENTER, new JScrollPane(canvas));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(600, 600));

        
//        canvas.setPreferredSize(new Dimension(1024, 576));
  //      canvas.setMinimumSize(new Dimension(1024, 576));

        validate();
        repaint();
        pack();       
        setVisible(true);
        
        try {
			celestialObjects = new CelestialObjects(canvas);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
        
        updateTimerMenu();
        updateSpeedMenu(0);
        
		props = new Properties();
		if (propsFile.exists()) {
			try {
				FileInputStream fis = new FileInputStream(propsFile);
				props.load(fis);
				fis.close();
			} catch (Exception e) {
			}
			
			setFromProps();
		} else {
	        railwayStyleMenu.setSelected(celestialObjects.isRailwayStyleClock());
			zenithAnglesMenu.setSelected(celestialObjects.isRenderContourLines());
			updateDayNightsMenu();
		}
		
        updateObjects();
        
        if (timerRunning == false)
        	toggleTimer();
		
		addKeyListener(this);
	}
	
	private void saveSettingsToProps() {
		try {
			FileOutputStream fos = new FileOutputStream(propsFile);
			props.store(fos, "");
			fos.close();
		} catch (Exception e) {
		}
	}
	
	private Double getPropertiesLatitude() {
		Double d;
		try {
			d = new Double(props.getProperty("latitude", "0.0"));
		} catch (Exception e) {
			d = new Double(0.0);
		}
		return d;
	}
	
	private void setPropertiesLatitude(Double d) {
    	props.setProperty("latitude", d.toString());
    	saveSettingsToProps();
	}
	
	private Double getPropertiesLongitude() {
		Double d;
		try {
			d = new Double(props.getProperty("longitude", "0.0"));
		} catch (Exception e) {
			d = new Double(0.0);
		}
		return d;
	}
	
	private void setPropertiesLongitude(Double d) {
    	props.setProperty("longitude", d.toString());
    	saveSettingsToProps();
	}
	
	private boolean stringToBoolean(String value) {
		if ("1".equals(value))
			return true;
		
		return false;
	}
	
	private boolean getRailwayStyle(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("railwaystyle", (defaultSelected ? "1" : "0")));
	}
	
	private void setRailwayStyle(boolean selected) {
    	props.setProperty("railwaystyle", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private boolean getTwentyFourHourStyle(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("twentyfourhourstyle", (defaultSelected ? "1" : "0")));
	}
	
	private void setTwentyFourHourStyle(boolean selected) {
    	props.setProperty("twentyfourhourstyle", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private boolean getZenithAngles(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("showcontourlines", (defaultSelected ? "1" : "0")));
	}
	
	private void setZenithAngles(boolean selected) {
    	props.setProperty("showcontourlines", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private String getDayNightOpacity(String defaultOpacity) {
		return props.getProperty("daynightopacity", defaultOpacity);
	}
	
	private void setDayNightOpacity(boolean rendered, int fillLevel) {
		String setting = "0";
		
		if (rendered) {
			if (fillLevel == 224)
				setting = "1";
			else if (fillLevel == 255)
				setting = "2";
		}
			
    	props.setProperty("daynightopacity", setting);
    	saveSettingsToProps();
	}
	
	private boolean getMickeyFace(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("usemickeyface", (defaultSelected ? "1" : "0")));
	}
	
	private void setMickeyFace(boolean selected) {
    	props.setProperty("usemickeyface", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private void setFromProps() {
		celestialObjects.setLatitude(getPropertiesLatitude());
		celestialObjects.setLongitude(getPropertiesLongitude());
		boolean tempBoolean = getRailwayStyle(true);
		celestialObjects.setRailwayStyleClock(tempBoolean);
		railwayStyleMenu.setSelected(tempBoolean);

		tempBoolean = getTwentyFourHourStyle(true);
		celestialObjects.setTwentyFourHourClock(tempBoolean);
		twentyFourHourStyleMenu.setSelected(tempBoolean);

		tempBoolean = getZenithAngles(true);
		celestialObjects.setRenderContourLines(tempBoolean);
		zenithAnglesMenu.setSelected(tempBoolean);

		
		String opacity = getDayNightOpacity("1");
		if ("0".equals(opacity)) {
			celestialObjects.setDayNightRendered(false);
		} else if ("1".equals(opacity)) {
			celestialObjects.setDayNightRendered(true);
			celestialObjects.setDayNightFillLevel(224);
		} else if ("2".equals(opacity)) {
			celestialObjects.setDayNightRendered(true);
			celestialObjects.setDayNightFillLevel(255);
		}
		updateDayNightsMenu();
		
		mickeyFace = getMickeyFace(true);
        mickeyFaceMenu.setSelected(mickeyFace);
        celestialObjects.setClockFace(mickeyFace);
    }
	
	private void updateDayNightsMenu() {
		if (! celestialObjects.isDayNightRendered()) {
			dayNightOffMenu.setSelected(true);
			dayNightPartialMenu.setSelected(false);
			dayNightFullMenu.setSelected(false);
		} else {
			dayNightOffMenu.setSelected(false);
			
			if (celestialObjects.getDayNightFillLevel() == 224) {
				dayNightPartialMenu.setSelected(true);
				dayNightFullMenu.setSelected(false);
			}
			else {
				dayNightPartialMenu.setSelected(false);
				dayNightFullMenu.setSelected(true);
			}				
		}
	}
	
	private void updateSpeedMenu() {
		updateSpeedMenu(celestialObjects.getCurrentSpeed());
	}
	
	private void updateSpeedMenu(int speedIndex) {
		for (int scanSpeeds = 0;scanSpeeds < speedSettings.length;scanSpeeds ++) {
			speedSettings[scanSpeeds].setSelected(scanSpeeds == speedIndex);
		}
		
		int newInterval;
		int speedHint = celestialObjects.animateSpeedHint();
		if (speedHint == 0)
			newInterval = 1000;
		else if (speedHint == 1)
			newInterval = 16;
		else
			newInterval = 250;
		if (newInterval != timerInterval) {
			timerInterval = newInterval;
			toggleTimer();
			toggleTimer();
		}
	}
	
	private void updateTimerMenu() {
		timerMenu.setSelected(timerRunning);
	}
	
	private void updateObjects() {
		try {
			celestialObjects.updateObjects();
		} catch (Exception e) {
		}
	}
	
	private void redrawClock() {
		if ((! timerRunning) && (! canvas.isCurrentlyRendering()))
			updateObjects();
	}

	private void toggleTimer()
	{
		if (! timerRunning)
		{
			timerRunning = true;
			timer = new Timer();
			animationTimer = new AnimationTimer(this);
			timer.schedule(animationTimer, 0, timerInterval);
		}
		else
		{
			timerRunning = false;
			timer.cancel();
			timer = null;
			animationTimer = null;
		}
		
        updateTimerMenu();
	}

	public void animate(me.qbert.skywatch.util.AnimationTimer timer)
	{
		if (! canvas.isCurrentlyRendering())
			updateObjects();
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
		
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			device.setFullScreenWindow(null);
			menubar.setVisible(true);
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}
}
