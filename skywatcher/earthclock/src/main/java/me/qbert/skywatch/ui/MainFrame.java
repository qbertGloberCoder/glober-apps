package me.qbert.skywatch.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

public class MainFrame extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2330068972046366353L;
	
	private JCheckBoxMenuItem timerMenu;
	private JCheckBoxMenuItem railwayStyleMenu;
	
	private Canvas canvas;
	
	private boolean timerRunning = false;
    private Timer timer = null;
    private AnimationTimer animationTimer = null;
    
    private int timerInterval = 1000;
    
    private CelestialObjects celestialObjects;
	
	public MainFrame() {
		super("Multi-transformation earth clock");
		
        //Creating the MenuBar and adding components
        JMenuBar mb = new JMenuBar();
        JMenu m1 = new JMenu("File");
        mb.add(m1);
        JMenuItem m11 = new JMenuItem("Exit");
        m1.add(m11);

        JMenu m2 = new JMenu("Options");
        mb.add(m2);
        timerMenu = new JCheckBoxMenuItem("Timer");
        m2.add(timerMenu);
        railwayStyleMenu = new JCheckBoxMenuItem("Swiss railway style");
        m2.add(railwayStyleMenu);

        JMenu m3 = new JMenu("Speed");
        mb.add(m3);
        JMenuItem timerSpeedDown = new JMenuItem("Slow Down");
        m3.add(timerSpeedDown);
        JMenuItem timerSpeedUp = new JMenuItem("Speed Up");
        m3.add(timerSpeedUp);

        JMenu m4 = new JMenu("Location");
        mb.add(m4);
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
			}
		});
        
        timerSpeedDown.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.speedDown();
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
		});
        
        timerSpeedUp.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				celestialObjects.speedUp();
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
		});
        
        latPicker.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFrame f = new JFrame();   
			    String latitude = JOptionPane.showInputDialog(f, "Enter Latitude");
			    try {
			    	Double d = new Double(latitude);
			    	celestialObjects.setLatitude(d);
			    } catch (NumberFormatException ex) {
			    	
			    }
			}
		});
        
        lonPicker.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFrame f = new JFrame();   
			    String longitude = JOptionPane.showInputDialog(f, "Enter Longitude");
			    try {
			    	Double d = new Double(longitude);
			    	celestialObjects.setLongitude(d);
			    } catch (NumberFormatException ex) {
			    	
			    }
			}
		});
        
        updateTimerMenu();

        canvas = new Canvas();
        
        //Adding Components to the frame.
        getContentPane().add(BorderLayout.NORTH, mb);
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
        updateObjects();
        
        if (timerRunning == false)
        	toggleTimer();
        railwayStyleMenu.setSelected(celestialObjects.isRailwayStyleClock());
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
		updateObjects();
	}
}
