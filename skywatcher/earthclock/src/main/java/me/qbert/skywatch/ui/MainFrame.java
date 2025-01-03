package me.qbert.skywatch.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.Timer;

import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import me.qbert.skywatch.Main;
import me.qbert.skywatch.service.AbstractCelestialObjects;
import me.qbert.skywatch.service.AzimuthalEquidistantNPPObjects;
import me.qbert.skywatch.service.AzimuthalEquidistantSPPObjects;
import me.qbert.skywatch.service.EquirectilinearObjects;
import me.qbert.skywatch.service.GlobeObjects;
import me.qbert.skywatch.service.MercatorObjects;
import me.qbert.skywatch.service.StereoAzimuthalObjects;
import me.qbert.skywatch.service.StereoGlobeObjects;
import me.qbert.skywatch.service.AbstractCelestialObjects.MapCenterMode;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.util.AnimationTimer;

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
	
	private JCheckBoxMenuItem aeMapNProjection;
	private JCheckBoxMenuItem aeMapNStereoProjection;
	private JCheckBoxMenuItem aeMapSProjection;
	private JCheckBoxMenuItem equirectilinearMapProjection;
	private JCheckBoxMenuItem mercatorMapProjection;
	private JCheckBoxMenuItem globeProjection;
	private JCheckBoxMenuItem stereoProjection;
	private JCheckBoxMenuItem stereoXProjection;
	
	private JMenu centerMapMenu;
	private JCheckBoxMenuItem [] centerModes;
	private JCheckBoxMenuItem globeFullSize;
	private JCheckBoxMenuItem showClockMenu;

	private JCheckBoxMenuItem showSolarSystem;
	private JCheckBoxMenuItem showPlanetsToScale;
	private JCheckBoxMenuItem showClock;
	
	private JCheckBoxMenuItem timerMenu;
	private JCheckBoxMenuItem railwayStyleMenu;
	private JCheckBoxMenuItem twentyFourHourStyleMenu;
	private JCheckBoxMenuItem renderSunGPGreatCircleRouteMenu;
	private JCheckBoxMenuItem renderMoonGPGreatCircleRouteMenu;
	private JCheckBoxMenuItem sunZenithAnglesMenu;
	private JCheckBoxMenuItem moonZenithAnglesMenu;
	private JCheckBoxMenuItem [] sunPaths;
	private JCheckBoxMenuItem [] moonPaths;
	private JCheckBoxMenuItem [] speedSettings;
	
    private JCheckBoxMenuItem dayNightOffMenu;
    private JCheckBoxMenuItem dayNightPartialMenu;
    private JCheckBoxMenuItem dayNightFullMenu;

    private JCheckBoxMenuItem moonDayNightOffMenu;
    
    private JCheckBoxMenuItem normalClockFaceMenu;
    private JCheckBoxMenuItem mickeyFaceMenu;
    private JCheckBoxMenuItem sillyWalkFaceMenu;
    
    private JCheckBoxMenuItem justifyLeftMenu;
    private JCheckBoxMenuItem justifyCenterMenu;
    private JCheckBoxMenuItem justifyRightMenu;
    
    private JCheckBoxMenuItem orientNormalMenu;
    private JCheckBoxMenuItem orientMapShiftMenu;
    private JCheckBoxMenuItem orientDSTAdjustMenu;
    
    private JCheckBoxMenuItem runningScript;

	private Canvas canvas;
	
	private boolean timerRunning = false;
    private Timer timer = null;
    private AnimationTimer animationTimer = null;
    
    private int timerInterval = 1000;
    
    private AbstractCelestialObjects celestialObjects;
//    private CelestialObjects celestialObjects;
    
    private AbstractCelestialObjects.ClockFaces clockFace = AbstractCelestialObjects.ClockFaces.MICKEYMOUSE;
    
    private BufferedImage image = null;
    private boolean exportMode = false;
    
    private Properties props;
    private static GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0];
    private static File propsFile = new File("settings.properties");
	
    private int frame = 1;
    
	public MainFrame() {
		super("Multi-transformation earth clock");
		
		Logger logger = LogManager.getLogger(Main.class.getName());
		
		logger.trace("TRACE GOES HERE");
		logger.log(Level.ALL, "Test log here");

		//Creating the MenuBar and adding components
        menubar = new JMenuBar();
        JMenu m1 = new JMenu("File");
        menubar.add(m1);
        JMenuItem m11 = new JMenuItem("Exit");
        m1.add(m11);

        JMenu m15 = new JMenu("Projection");
        menubar.add(m15);
        
    	aeMapNProjection = new JCheckBoxMenuItem("AE map (North Pole)");
    	m15.add(aeMapNProjection);
    	aeMapNProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(0);
				setProjection(0);
			}
		});
    	aeMapNStereoProjection = new JCheckBoxMenuItem("AE map Stereo (North Pole)");
    	m15.add(aeMapNStereoProjection);
    	aeMapNStereoProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(7);
				setProjection(7);
			}
		});
    	aeMapSProjection = new JCheckBoxMenuItem("AE map (South Pole)");
    	m15.add(aeMapSProjection);
    	aeMapSProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(2);
				setProjection(2);
			}
		});
    	equirectilinearMapProjection = new JCheckBoxMenuItem("Equirectilinear");
    	m15.add(equirectilinearMapProjection);
    	equirectilinearMapProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(1);
				setProjection(1);
			}
		});
    	mercatorMapProjection = new JCheckBoxMenuItem("Mercator");
    	m15.add(mercatorMapProjection);
    	mercatorMapProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(6);
				setProjection(6);
			}
		});
    	globeProjection = new JCheckBoxMenuItem("Globe");
    	m15.add(globeProjection);
    	globeProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(3);
				setProjection(3);
			}
		});
    	stereoProjection = new JCheckBoxMenuItem("Stereo (far)");
    	m15.add(stereoProjection);
    	stereoProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(4);
				setProjection(4);
			}
		});
    	stereoXProjection = new JCheckBoxMenuItem("Stereo (near)");
    	m15.add(stereoXProjection);
    	stereoXProjection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				initProjection(5);
				setProjection(5);
			}
		});
        

    	JMenu m16 = new JMenu("View");
        menubar.add(m16);
        
        centerMapMenu = new JMenu("Center");
        m16.add(centerMapMenu);
        
        MapCenterMode [] centerMapModes = AbstractCelestialObjects.MAP_CENTER_MODES;
        
        centerModes = new JCheckBoxMenuItem[centerMapModes.length];
        for (int i = 0;i < centerMapModes.length;i ++) {
        	centerModes[i] = new JCheckBoxMenuItem(centerMapModes[i].toString());
        	final MapCenterMode mm = centerMapModes[i];
        	final int idx = i;
        	centerModes[i].addActionListener(new ActionListener() {
    			@Override
    			public void actionPerformed(ActionEvent e) {
    				celestialObjects.setCenterLocation(mm);
    				updateCenterModes(idx);
    			}
    		});
        	centerMapMenu.add(centerModes[i]);
        }

        globeFullSize = new JCheckBoxMenuItem("Globe full size");
        m16.add(globeFullSize);
        globeFullSize.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setFullSize(! celestialObjects.isFullSize());
			}
		});

        showClockMenu = new JCheckBoxMenuItem("Clock");
        m16.add(showClockMenu);
        showClockMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean showClock = !celestialObjects.isShowClock();
				celestialObjects.setShowClock(showClock);
				showClockMenu.setSelected(showClock);
			}
		});
        
        
        showSolarSystem = new JCheckBoxMenuItem("Date (solar system)");
        m16.add(showSolarSystem);
        showSolarSystem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean show = !celestialObjects.isPlanetsVisible();
				celestialObjects.setPlanetsVisible(show);
				showSolarSystem.setSelected(show);
			}
		});
        
        
        showPlanetsToScale = new JCheckBoxMenuItem("Planet orbits to scale");
        m16.add(showPlanetsToScale);
        showPlanetsToScale.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean show = !celestialObjects.isPlanetsToScale();
				celestialObjects.setPlanetsToScale(show);
				showPlanetsToScale.setSelected(show);
			}
		});
        
        showClock = new JCheckBoxMenuItem("Time (Earth clock)");
        m16.add(showClock);
        showClock.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean show = !celestialObjects.isClockVisible();
				celestialObjects.setClockVisible(show);
				showClock.setSelected(show);
			}
		});
        
        JMenu exportSizeMenu = new JMenu("Export size");
        m16.add(exportSizeMenu);

        JCheckBoxMenuItem size1080Menu = new JCheckBoxMenuItem("1920x1080");
        exportSizeMenu.add(size1080Menu);

        JCheckBoxMenuItem size540Menu = new JCheckBoxMenuItem("960x540");
        exportSizeMenu.add(size540Menu);
        
        size1080Menu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
				size1080Menu.setSelected(true);
				size540Menu.setSelected(false);
			}
		});
        size540Menu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
				size1080Menu.setSelected(false);
				size540Menu.setSelected(true);
			}
		});
        

    	
        JMenu m2 = new JMenu("Options");
        menubar.add(m2);

        JMenuItem fullscreenMenu = new JMenuItem("Full screen");
        m2.add(fullscreenMenu);

        JCheckBoxMenuItem recordMenu = new JCheckBoxMenuItem("Save to outputs");
        m2.add(recordMenu);

        m2.add(new JSeparator());
        
        recordMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				exportMode = ! exportMode;
				recordMenu.setSelected(exportMode);
			}
		});


        JMenu subMenu = new JMenu("Justify");
        m2.add(subMenu);
        justifyLeftMenu = new JCheckBoxMenuItem("Left");
        subMenu.add(justifyLeftMenu);
        justifyCenterMenu = new JCheckBoxMenuItem("Center");
        subMenu.add(justifyCenterMenu);
        justifyRightMenu = new JCheckBoxMenuItem("Right");
        subMenu.add(justifyRightMenu);
        
        justifyLeftMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setShiftDirection(-1);
				updateJustifyMenu(celestialObjects.getShiftDirection());
				setShiftDirection(celestialObjects.getShiftDirection());
			}
		});

        justifyCenterMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setShiftDirection(0);
				updateJustifyMenu(celestialObjects.getShiftDirection());
				setShiftDirection(celestialObjects.getShiftDirection());
			}
		});

        justifyRightMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setShiftDirection(1);
				updateJustifyMenu(celestialObjects.getShiftDirection());
				setShiftDirection(celestialObjects.getShiftDirection());
			}
		});

        subMenu = new JMenu("Orientation");
        m2.add(subMenu);
        orientNormalMenu = new JCheckBoxMenuItem("Normal");
        subMenu.add(orientNormalMenu);
        orientMapShiftMenu = new JCheckBoxMenuItem("DST shift map");
        subMenu.add(orientMapShiftMenu);
        orientDSTAdjustMenu = new JCheckBoxMenuItem("DST time adjust");
        subMenu.add(orientDSTAdjustMenu);
        
        orientNormalMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setDstRotateSetting(0);
				updateOrientationMenu(celestialObjects.getDstRotateSetting());
				setOrientation(celestialObjects.getDstRotateSetting());
			}
		});
        orientMapShiftMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setDstRotateSetting(1);
				updateOrientationMenu(celestialObjects.getDstRotateSetting());
				setOrientation(celestialObjects.getDstRotateSetting());
			}
		});
        orientDSTAdjustMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setDstRotateSetting(2);
				updateOrientationMenu(celestialObjects.getDstRotateSetting());
				setOrientation(celestialObjects.getDstRotateSetting());
			}
		});

        m2.add(new JSeparator());
        
        timerMenu = new JCheckBoxMenuItem("Timer");
        m2.add(timerMenu);
        railwayStyleMenu = new JCheckBoxMenuItem("Swiss railway style");
        m2.add(railwayStyleMenu);
        
        twentyFourHourStyleMenu = new JCheckBoxMenuItem("24 hour style clock");
        m2.add(twentyFourHourStyleMenu);
        twentyFourHourStyleMenu.setSelected(true);
        
        normalClockFaceMenu = new JCheckBoxMenuItem("Normal clock face");
        m2.add(normalClockFaceMenu);
        normalClockFaceMenu.setSelected(false);
        
        mickeyFaceMenu = new JCheckBoxMenuItem("Mickey Mouse face");
        m2.add(mickeyFaceMenu);
        mickeyFaceMenu.setSelected(false);
        
        sillyWalkFaceMenu = new JCheckBoxMenuItem("Silly Walk face");
        m2.add(sillyWalkFaceMenu);
        sillyWalkFaceMenu.setSelected(false);
        
        m2.add(new JSeparator());
        
        sunZenithAnglesMenu = new JCheckBoxMenuItem("Sun zenith angles");
        m2.add(sunZenithAnglesMenu);
        moonZenithAnglesMenu = new JCheckBoxMenuItem("Moon zenith angles");
        m2.add(moonZenithAnglesMenu);
        renderSunGPGreatCircleRouteMenu = new JCheckBoxMenuItem("Sun GP great circle routes");
        m2.add(renderSunGPGreatCircleRouteMenu);
        renderMoonGPGreatCircleRouteMenu = new JCheckBoxMenuItem("Moon GP great circle routes");
        m2.add(renderMoonGPGreatCircleRouteMenu);
        subMenu = new JMenu("Sun day/night opacity");
        m2.add(subMenu);
        dayNightOffMenu = new JCheckBoxMenuItem("Off");
        subMenu.add(dayNightOffMenu);
        dayNightPartialMenu = new JCheckBoxMenuItem("Partial");
        subMenu.add(dayNightPartialMenu);
        dayNightFullMenu = new JCheckBoxMenuItem("Full");
        subMenu.add(dayNightFullMenu);
        
        moonDayNightOffMenu = new JCheckBoxMenuItem("Moon day/night opacity");
        m2.add(moonDayNightOffMenu);

        String [] objectPathModes = AbstractCelestialObjects.getPrecessionPathModeLabels();
        subMenu = new JMenu("Sun path");
        m2.add(subMenu);
        sunPaths = new JCheckBoxMenuItem[objectPathModes.length];
        for (int i = 0;i < objectPathModes.length;i ++) {
        	sunPaths[i] = new JCheckBoxMenuItem(objectPathModes[i]);
        	subMenu.add(sunPaths[i]);
        	
        	final int index = i;
        	sunPaths[i].addActionListener(new ActionListener() {
    			@Override
    			public void actionPerformed(ActionEvent e) {
    				celestialObjects.setSunPrecessionPathMode(index);
    				updateSunPathMenu(index);
    				setSunPrecessionPathMode(index);
    			}
    		});
        }
        
        subMenu = new JMenu("Moon path");
        m2.add(subMenu);
        moonPaths = new JCheckBoxMenuItem[objectPathModes.length];
        for (int i = 0;i < objectPathModes.length;i ++) {
        	moonPaths[i] = new JCheckBoxMenuItem(objectPathModes[i]);
        	subMenu.add(moonPaths[i]);
        	
        	final int index = i;
        	moonPaths[i].addActionListener(new ActionListener() {
    			@Override
    			public void actionPerformed(ActionEvent e) {
    				celestialObjects.setMoonPrecessionPathMode(index);
    				updateMoonPathMenu(index);
    				setMoonPrecessionPathMode(index);
    			}
    		});
        }
        
        JMenu m3 = new JMenu("Speed");
        menubar.add(m3);
        JMenuItem timerSpeedDown = new JMenuItem("Slow Down");
        m3.add(timerSpeedDown);
        JMenuItem timerSpeedUp = new JMenuItem("Speed Up");
        m3.add(timerSpeedUp);
        
        m3.add(new JSeparator());
        
        String [] speedLabels = AbstractCelestialObjects.getSpeedLabels();
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

        runningScript = new JCheckBoxMenuItem("Script");
        menubar.add(runningScript);
        runningScript.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (celestialObjects.isRunningScript()) {
					celestialObjects.abortScript();
					runningScript.setSelected(false);
				} else {
					runningScript.setSelected(true);
				}
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
        
        renderSunGPGreatCircleRouteMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setRenderSunGPGreatCircleRoute(! celestialObjects.isRenderSunGPGreatCircleRoute());
				renderSunGPGreatCircleRouteMenu.setSelected(celestialObjects.isRenderSunGPGreatCircleRoute());
				redrawClock();
				setSunGPGreatCircleRoutes(celestialObjects.isRenderSunGPGreatCircleRoute());
			}
		});
        
        renderMoonGPGreatCircleRouteMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setRenderMoonGPGreatCircleRoute(! celestialObjects.isRenderMoonGPGreatCircleRoute());
				renderMoonGPGreatCircleRouteMenu.setSelected(celestialObjects.isRenderMoonGPGreatCircleRoute());
				redrawClock();
				setMoonGPGreatCircleRoutes(celestialObjects.isRenderMoonGPGreatCircleRoute());
			}
		});
        
        sunZenithAnglesMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setSunRenderContourLines(! celestialObjects.isSunRenderContourLines());
				sunZenithAnglesMenu.setSelected(celestialObjects.isSunRenderContourLines());
				redrawClock();
				setSunZenithAngles(celestialObjects.isSunRenderContourLines());
			}
		});
        
        moonZenithAnglesMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setMoonRenderContourLines(! celestialObjects.isMoonRenderContourLines());
				moonZenithAnglesMenu.setSelected(celestialObjects.isMoonRenderContourLines());
				redrawClock();
				setMoonZenithAngles(celestialObjects.isMoonRenderContourLines());
			}
		});
        
        normalClockFaceMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				clockFace = AbstractCelestialObjects.ClockFaces.NEEDLEHANDS;
				normalClockFaceMenu.setSelected(true);
				mickeyFaceMenu.setSelected(false);
				sillyWalkFaceMenu.setSelected(false);
				celestialObjects.setClockFace(AbstractCelestialObjects.ClockFaces.NEEDLEHANDS);
				redrawClock();
				setClockFace(clockFace);
			}
		});
        
        mickeyFaceMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				clockFace = AbstractCelestialObjects.ClockFaces.MICKEYMOUSE;
				normalClockFaceMenu.setSelected(false);
				mickeyFaceMenu.setSelected(true);
				sillyWalkFaceMenu.setSelected(false);
				celestialObjects.setClockFace(AbstractCelestialObjects.ClockFaces.MICKEYMOUSE);
				redrawClock();
				setClockFace(clockFace);
			}
		});
        
        sillyWalkFaceMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				clockFace = AbstractCelestialObjects.ClockFaces.SILLYWALKS;
				normalClockFaceMenu.setSelected(false);
				mickeyFaceMenu.setSelected(false);
				sillyWalkFaceMenu.setSelected(true);
				celestialObjects.setClockFace(AbstractCelestialObjects.ClockFaces.SILLYWALKS);
				redrawClock();
				setClockFace(clockFace);
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
        
        moonDayNightOffMenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.setMoonDayNightRendered(! celestialObjects.isMoonDayNightRendered());
				updateMoonDayNightsMenu();
				redrawClock();
				setMoonDayNightOpacity(celestialObjects.isMoonDayNightRendered());
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
        
        // 1920x1080
        // 960x540
		image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
//		image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
		
		size1080Menu.setSelected(true);
        
        //Adding Components to the frame.
        getContentPane().add(BorderLayout.NORTH, menubar);
        getContentPane().add(BorderLayout.CENTER, new JScrollPane(canvas));
        
        JScrollBar sb = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -7200, 7200);
        getContentPane().add(BorderLayout.SOUTH, new JScrollPane(sb));
        
        sb.addAdjustmentListener(new AdjustmentListener() {
			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) {
				celestialObjects.setTimeBias(sb.getValue());
			}
		});
        

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(600, 600));

        
//        canvas.setPreferredSize(new Dimension(1024, 576));
  //      canvas.setMinimumSize(new Dimension(1024, 576));

        validate();
        repaint();
        pack();       
        setVisible(true);
        
		aeMapNProjection.setSelected(true);
		aeMapNStereoProjection.setSelected(false);
		aeMapSProjection.setSelected(false);
		mercatorMapProjection.setSelected(false);
		equirectilinearMapProjection.setSelected(false);
		globeProjection.setSelected(false);
		stereoProjection.setSelected(false);
		stereoXProjection.setSelected(false);
        initProjection(-1);
        
        if (timerRunning == false)
        	toggleTimer();
		
		addKeyListener(this);
	}
	
	private void initProjection(int projectionIndex) {
		if ((celestialObjects != null) && (celestialObjects.isRunningScript()))
			return;
		
        boolean reloadProps = (props == null);
        if (reloadProps)
        	props = new Properties();
        
		if (propsFile.exists()) {
			if (reloadProps) {
				try {
					FileInputStream fis = new FileInputStream(propsFile);
					props.load(fis);
					fis.close();
				} catch (Exception e) {
				}
			}
		}
		
		if (projectionIndex < 0) {
			projectionIndex = getProjection("0");
		}

        try {
        	if (projectionIndex == 0) {
        		celestialObjects = new AzimuthalEquidistantNPPObjects(canvas);
        	}
        	else if (projectionIndex == 7) {
        		celestialObjects = new StereoAzimuthalObjects(canvas);
        		((StereoAzimuthalObjects)celestialObjects).setStereoAngle(-8);
        	}
        	else if (projectionIndex == 1)
        		celestialObjects = new EquirectilinearObjects(canvas);
        	else if (projectionIndex == 6)
        		celestialObjects = new MercatorObjects(canvas);
        	else if (projectionIndex == 2)
        		celestialObjects = new AzimuthalEquidistantSPPObjects(canvas);
        	else if (projectionIndex == 3)
        		celestialObjects = new GlobeObjects(canvas);
        	else if (projectionIndex == 4) {
        		celestialObjects = new StereoGlobeObjects(canvas);
        		((StereoGlobeObjects)celestialObjects).setStereoAngle(1.5);
        	}
        	else if (projectionIndex == 5) {
        		celestialObjects = new StereoGlobeObjects(canvas);
        		((StereoGlobeObjects)celestialObjects).setStereoAngle(-1.5);
        	}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
        
        updateTimerMenu();
        celestialObjects.setSpeed(1);
        updateSpeedMenu(1);
        
		if (propsFile.exists())
			setFromProps();
		 else {
	        railwayStyleMenu.setSelected(celestialObjects.isRailwayStyleClock());
			sunZenithAnglesMenu.setSelected(celestialObjects.isSunRenderContourLines());
			moonZenithAnglesMenu.setSelected(celestialObjects.isMoonRenderContourLines());
			renderSunGPGreatCircleRouteMenu.setSelected(celestialObjects.isRenderSunGPGreatCircleRoute());
			renderMoonGPGreatCircleRouteMenu.setSelected(celestialObjects.isRenderMoonGPGreatCircleRoute());
			updateDayNightsMenu();
			updateMoonDayNightsMenu();
		 }
		
		updateSunPathMenu();
		updateMoonPathMenu();
        updateObjects();

        if (projectionIndex == 0) {
			aeMapNProjection.setSelected(true);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(false);
			equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(false);
			centerModes[0].setEnabled(false);
			globeFullSize.setEnabled(false);
        } else if (projectionIndex == 1) {
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(false);
        	equirectilinearMapProjection.setSelected(true);
			globeProjection.setSelected(false);
			centerModes[0].setEnabled(false);
			globeFullSize.setEnabled(false);
        } else if (projectionIndex == 2) {
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(true);
        	equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(false);
			centerModes[0].setEnabled(false);
			globeFullSize.setEnabled(false);
        } else if (projectionIndex == 3) {
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(false);
        	equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(true);
			centerModes[0].setEnabled(true);
			globeFullSize.setEnabled(true);
        } else if (projectionIndex == 4) { // stereo
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(true);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(false);
        	equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(false);
			centerModes[0].setEnabled(true);
			globeFullSize.setEnabled(true);
        } else if (projectionIndex == 5) { // stereo X
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(true);
			aeMapSProjection.setSelected(false);
        	equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(false);
			centerModes[0].setEnabled(true);
			globeFullSize.setEnabled(true);
        } else if (projectionIndex == 6) { // meractor
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(true);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(false);
        	equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(false);
			centerModes[0].setEnabled(true);
			globeFullSize.setEnabled(true);
        } else if (projectionIndex == 7) { // AE stereo
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(true);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(false);
        	equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(false);
			centerModes[0].setEnabled(true);
			globeFullSize.setEnabled(true);
        } else {
        	aeMapNProjection.setSelected(false);
			aeMapNStereoProjection.setSelected(false);
			mercatorMapProjection.setSelected(false);
			stereoProjection.setSelected(false);
			stereoXProjection.setSelected(false);
			aeMapSProjection.setSelected(false);
        	equirectilinearMapProjection.setSelected(false);
			globeProjection.setSelected(true);
			centerModes[0].setEnabled(true);
			globeFullSize.setEnabled(true);
        }
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
	
	private void setSunGPGreatCircleRoutes(boolean selected) {
    	props.setProperty("sungpgreatcircleroute", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private boolean getSunGPGreatCircleRoutes(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("sungpgreatcircleroute", (defaultSelected ? "1" : "0")));
	}
	
	private void setMoonGPGreatCircleRoutes(boolean selected) {
    	props.setProperty("moongpgreatcircleroute", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private boolean getMoonGPGreatCircleRoutes(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("moongpgreatcircleroute", (defaultSelected ? "1" : "0")));
	}
	
	private boolean getSunZenithAngles(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("showcontourlines", (defaultSelected ? "1" : "0")));
	}
	
	private void setSunZenithAngles(boolean selected) {
    	props.setProperty("showcontourlines", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private boolean getMoonZenithAngles(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("showmooncontourlines", (defaultSelected ? "1" : "0")));
	}
	
	private void setMoonZenithAngles(boolean selected) {
    	props.setProperty("showmooncontourlines", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private int getSunPrecessionPathMode(String defaultPathMode) {
		Integer i;
		try {
			i = new Integer(props.getProperty("sunprecessionpathmode", defaultPathMode));
		} catch (Exception e) {
			i = new Integer(0);
		}
		return i.intValue();

	}
	
	private void setSunPrecessionPathMode(int pathMode) {
    	props.setProperty("sunprecessionpathmode", Integer.toString(pathMode));
    	saveSettingsToProps();
	}
	
	private int getMoonPrecessionPathMode(String defaultPathMode) {
		Integer i;
		try {
			i = new Integer(props.getProperty("moonprecessionpathmode", defaultPathMode));
		} catch (Exception e) {
			i = new Integer(0);
		}
		return i.intValue();

	}
	
	private void setMoonPrecessionPathMode(int pathMode) {
    	props.setProperty("moonprecessionpathmode", Integer.toString(pathMode));
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
	
	private boolean getMoonDayNightOpacity(boolean defaultSelected) {
		return stringToBoolean(props.getProperty("moondaynightopacity", (defaultSelected ? "1" : "0")));
	}
	
	private void setMoonDayNightOpacity(boolean selected) {
    	props.setProperty("moondaynightopacity", (selected ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private AbstractCelestialObjects.ClockFaces getClockFace(boolean defaultSelected) {
		if (stringToBoolean(props.getProperty("usemickeyface", (defaultSelected ? "1" : "0"))) == true)
			return AbstractCelestialObjects.ClockFaces.MICKEYMOUSE;
		
		if (stringToBoolean(props.getProperty("usesillywalkface", (defaultSelected ? "1" : "0"))) == true)
			return AbstractCelestialObjects.ClockFaces.SILLYWALKS;
		
		return AbstractCelestialObjects.ClockFaces.NEEDLEHANDS;
	}
	
	private void setClockFace(AbstractCelestialObjects.ClockFaces clockFace) {
    	props.setProperty("usemickeyface", ((clockFace == AbstractCelestialObjects.ClockFaces.MICKEYMOUSE) ? "1" : "0"));
    	props.setProperty("usesillywalkface", ((clockFace == AbstractCelestialObjects.ClockFaces.SILLYWALKS) ? "1" : "0"));
    	saveSettingsToProps();
	}
	
	private int getShiftDirection(String defaultShiftDirection) {
		Integer i;
		try {
			i = new Integer(props.getProperty("justify", defaultShiftDirection));
		} catch (Exception e) {
			i = new Integer(0);
		}
		return i.intValue();

	}
	
	private void setShiftDirection(int shiftDirection) {
    	props.setProperty("justify", Integer.toString(shiftDirection));
    	saveSettingsToProps();
	}
	
	private int getOrientation(String defaultOrientation) {
		Integer i;
		try {
			i = new Integer(props.getProperty("orientation", defaultOrientation));
		} catch (Exception e) {
			i = new Integer(0);
		}
		return i.intValue();

	}
	
	private void setProjection(int projection) {
    	props.setProperty("projection", Integer.toString(projection));
    	saveSettingsToProps();
	}
	
	private int getProjection(String defaultOrientation) {
		Integer i;
		try {
			i = new Integer(props.getProperty("projection", defaultOrientation));
		} catch (Exception e) {
			i = new Integer(0);
		}
		return i.intValue();

	}
	
	private void setOrientation(int orientation) {
    	props.setProperty("orientation", Integer.toString(orientation));
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

		tempBoolean = getSunGPGreatCircleRoutes(true);
		celestialObjects.setRenderSunGPGreatCircleRoute(tempBoolean);
		renderSunGPGreatCircleRouteMenu.setSelected(tempBoolean);

		tempBoolean = getMoonGPGreatCircleRoutes(true);
		celestialObjects.setRenderMoonGPGreatCircleRoute(tempBoolean);
		renderMoonGPGreatCircleRouteMenu.setSelected(tempBoolean);

		tempBoolean = getSunZenithAngles(true);
		celestialObjects.setSunRenderContourLines(tempBoolean);
		sunZenithAnglesMenu.setSelected(tempBoolean);

		tempBoolean = getMoonZenithAngles(true);
		celestialObjects.setMoonRenderContourLines(tempBoolean);
		moonZenithAnglesMenu.setSelected(tempBoolean);

		
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
		
		tempBoolean = getMoonDayNightOpacity(true);
		celestialObjects.setMoonDayNightRendered(tempBoolean);
		updateMoonDayNightsMenu();
		
		clockFace = getClockFace(true);
        normalClockFaceMenu.setSelected(clockFace == AbstractCelestialObjects.ClockFaces.NEEDLEHANDS);
        mickeyFaceMenu.setSelected(clockFace == AbstractCelestialObjects.ClockFaces.MICKEYMOUSE);
        sillyWalkFaceMenu.setSelected(clockFace == AbstractCelestialObjects.ClockFaces.SILLYWALKS);
        celestialObjects.setClockFace(clockFace);
        
        celestialObjects.setSunPrecessionPathMode(getSunPrecessionPathMode("0"));
        celestialObjects.setMoonPrecessionPathMode(getMoonPrecessionPathMode("0"));
        
        celestialObjects.setShiftDirection(getShiftDirection("0"));
        updateJustifyMenu(celestialObjects.getShiftDirection());
        
        celestialObjects.setDstRotateSetting(getOrientation("0"));
        updateOrientationMenu(celestialObjects.getDstRotateSetting());
    }
	
	private void updateCenterModes(int centerModeIndex) {
		for (int scanPaths = 0;scanPaths < centerModes.length;scanPaths ++) {
			centerModes[scanPaths].setSelected(scanPaths == centerModeIndex);
		}
	}
	
	private void updateCenterModesMenu() {
		MapCenterMode centerLocation = celestialObjects.getCenterLocation();
        MapCenterMode [] centerMapModes = AbstractCelestialObjects.MAP_CENTER_MODES;

        for (int i = 0;i < centerMapModes.length;i ++) {
        	if (centerLocation == centerMapModes[i]) {
        		updateCenterModes(i);
        		break;
        	}
        }
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
	
	private void updateMoonDayNightsMenu() {
		moonDayNightOffMenu.setSelected(celestialObjects.isMoonDayNightRendered());
	}
	
	private void updateSunPathMenu() {
		updateSunPathMenu(celestialObjects.getSunPrecessionPathMode());
	}
	
	private void updateJustifyMenu(int justifyIndex) {
		justifyLeftMenu.setSelected(justifyIndex < 0);
		justifyCenterMenu.setSelected(justifyIndex == 0);
		justifyRightMenu.setSelected(justifyIndex > 0);
	}
	
	private void updateOrientationMenu(int dstOrientation) {
	    orientNormalMenu.setSelected(dstOrientation == 0);
	    orientMapShiftMenu.setSelected(dstOrientation == 1);
	    orientDSTAdjustMenu.setSelected(dstOrientation == 2);
	}
	
	private void updateSunPathMenu(int pathModeIndex) {
		for (int scanPaths = 0;scanPaths < sunPaths.length;scanPaths ++) {
			sunPaths[scanPaths].setSelected(scanPaths == pathModeIndex);
		}
	}
	
	private void updateMoonPathMenu() {
		updateMoonPathMenu(celestialObjects.getMoonPrecessionPathMode());
	}
	
	private void updateMoonPathMenu(int pathModeIndex) {
		for (int scanPaths = 0;scanPaths < moonPaths.length;scanPaths ++) {
			moonPaths[scanPaths].setSelected(scanPaths == pathModeIndex);
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
			newInterval = 125;
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
	
	private BufferedImage stripAlphaChannel(BufferedImage image) {
	    BufferedImage clone = new BufferedImage(image.getWidth(),
	            image.getHeight(), BufferedImage.TYPE_INT_RGB);
	    Graphics2D g2d = clone.createGraphics();
	    g2d.drawImage(image, 0, 0, null);
	    g2d.dispose();
	    return clone;
	}

	private void updateObjects() {
		if (canvas.isCurrentlyRendering())
			return;
		
		try {
			canvas.setRecordMode(((exportMode) || (celestialObjects.isRunningScript())));
			
			celestialObjects.updateObjects(false);
			
			if ((exportMode) || (celestialObjects.isRunningScript())) {
				canvas.paintToImage(image, true);
				
				String fname = String.format("output/test_%06d.jpg", frame ++);
				
				ImageIO.write(stripAlphaChannel(image), "jpg", new File(fname));
			} else {
				canvas.clearRepaintPanelFromImage();
				if (frame > 1) {
					runningScript.setSelected(false);
					frame = 1;
				} else if (runningScript.isSelected()) {
					celestialObjects.runScript();
				}
			}
			canvas.repaint();
		} catch (Exception e) {
			e.printStackTrace();
			if (exportMode)
				exportMode = false;
		}
	}
	
	private void redrawClock() {
		if (! timerRunning)
			updateObjects();
	}

	private void toggleTimer()
	{
		if (! timerRunning)
		{
			celestialObjects.resetDayNightRender();
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
