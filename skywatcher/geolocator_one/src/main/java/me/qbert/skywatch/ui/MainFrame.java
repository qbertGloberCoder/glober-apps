package me.qbert.skywatch.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.controller.SensorLocator;
import me.qbert.skywatch.controller.SensorLocator.CurrentLocation;
import me.qbert.skywatch.controller.SensorTracker;
import me.qbert.skywatch.controller.SensorTracker.ChangeEntry;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.skywatch.util.AnimationTimer;
import me.qbert.ui.RendererI;
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
	
	private JMenuItem timerMenu;
	
	private Canvas canvas;
	private JTextArea console;
	private JTextField lat;
    private JTextField lon;
	
	private RenderComponentUtil renderUtility;
	
	private SensorTracker sensorTracker;
	
	private boolean timerRunning = false;
    private Timer timer = null;
    private AnimationTimer animationTimer = null;
	
	public MainFrame() {
		super("Geo-Locator simulator 1");
		
        //Creating the MenuBar and adding components
        JMenuBar mb = new JMenuBar();
        JMenu m1 = new JMenu("FILE");
        timerMenu = new JMenuItem("Timer");
        mb.add(m1);
        mb.add(timerMenu);
        JMenuItem m11 = new JMenuItem("Exit");
        m1.add(m11);
        
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
        
        updateTimerMenu();

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(600, 600));
        
        console = new JTextArea();
        console.setMinimumSize(new Dimension(600, 600));
        JScrollPane consoleScroller = new JScrollPane(console);
        consoleScroller.setPreferredSize(new Dimension(600, 600));

        JPanel panel = new JPanel(); // the panel is not visible in output
        JLabel label1 = new JLabel("Latitude");
        lat = new JTextField(10); // accepts upto 10 characters
        JLabel label2 = new JLabel("Longitude");
        lon = new JTextField(10); // accepts upto 10 characters
        
        panel.add(label1);
        panel.add(lat);
        panel.add(label2);
        panel.add(lon);
        
        lat.setText("0.0");
        lon.setText("0.0");
        
        //Adding Components to the frame.
        getContentPane().add(BorderLayout.NORTH, mb);
        getContentPane().add(BorderLayout.WEST, new JScrollPane(canvas));
        getContentPane().add(BorderLayout.EAST, consoleScroller);
        getContentPane().add(BorderLayout.SOUTH, panel);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(500, 40));
        validate();
        repaint();
        pack();       
        setVisible(true);
        
        renderUtility = new RenderComponentUtil(canvas);
        
        try {
        	sensorTracker = new SensorTracker();
        } catch (Exception e) {
        	timerMenu.setEnabled(false);
        }
        
	}
	
	private void updateTimerMenu() {
		if (timerRunning)
			timerMenu.setText("Timer off");
		else
			timerMenu.setText("Timer on");
	}

	private void toggleTimer()
	{
		if (! timerRunning)
		{
			try {
				Double d = new Double(this.lat.getText());
				double lat = d.doubleValue();
				d = new Double(this.lon.getText());
				double lon = d.doubleValue();
				
				sensorTracker.setLocation(lat, lon);
			} catch (NumberFormatException e) {
			}
			timerRunning = true;
			timer = new Timer();
			animationTimer = new AnimationTimer(this);
			timer.schedule(animationTimer, 20, 20);
		}
		else
		{
			timerRunning = false;
			timer.cancel();
			timer = null;
			animationTimer = null;
			
			sensorTracker.reset();
		}
		
        updateTimerMenu();
	}

	public void animate(me.qbert.skywatch.util.AnimationTimer timer)
	{
		CurrentLocation location = null;
		
		try {
			location = sensorTracker.seekSun();
			updateControls(location);
		} catch (UninitializedObject e) {
		}
	}
	
	private void updateControls(CurrentLocation location) {
		if (location == null)
			return;
		
		List<RendererI> renderers = new ArrayList<RendererI>();
		
//		renderers.add(renderUtility.setColors(Color.GRAY, Color.GRAY));
		renderers.add(renderUtility.setColors(Color.BLACK, Color.BLACK));
		renderers.add(renderUtility.drawBox(0, 0, 1, 1, true));
//		renderers.add(renderUtility.setColors(Color.BLACK, Color.BLACK));
//		renderers.add(renderUtility.drawBox(0, 0, .5, .5, true));
//		renderers.add(renderUtility.drawBox(.5, .5, 1, 1, true));
		renderers.add(renderUtility.setColors(Color.WHITE, Color.WHITE));

		renderers.add(renderUtility.fillCircle(location.getSunXPct(), location.getSunYPct(), 10));

		renderers.add(renderUtility.setColors(Color.BLUE, Color.BLUE));
		renderers.add(renderUtility.fillCircle(location.getVisibleXPct(), location.getVisibleYPct(), 10));
		
		StringBuilder sb = new StringBuilder();
		sb.append("Debug\n");
		sb.append("Tracking: " + (sensorTracker.isPointingToSun() ? "yes" : "no") + "\n");
		sb.append("GeoLocation: " + sensorTracker.getLatitude() + " lat , " + sensorTracker.getLongitude() + " lon\n");
		sb.append("Sun currently at: " + location.getAltAz().getAltitude() + " alt, " + location.getAltAz().getAzimuth() + " az\n");
		sb.append("pointing at: " + location.getPointingAtAltAz().getAltitude() + " alt, " + location.getPointingAtAltAz().getAzimuth() + " az\n");
		sb.append("looking at: " + location.getVisibleXPct() + ", " + location.getVisibleYPct() + "\n");
		
		StringBuilder sb2 = new StringBuilder();
		
		double slopeTotals = 0.0;
		double slope;
		int slopeCount = 0;
		for (ChangeEntry ce : sensorTracker.getTrackChanges()) {
			sb2.append("Entry: ");
			sb2.append(ce.getSeconds());
			sb2.append(" seconds, ");
			sb2.append(ce.getAltAz().getAltitude());
			sb2.append(" alt delta, ");
			sb2.append(ce.getAltAz().getAzimuth());
			sb2.append(" az delta");
			if ((ce.getAltAz().getAltitude() != 0.0) && (ce.getAltAz().getAzimuth() != 0.0)) {
				sb2.append(" (slope: ");
				slope = Math.toDegrees(Math.atan2(ce.getAltAz().getAltitude(), ce.getAltAz().getAzimuth()));
				// important to figure out average....
				slopeTotals += slope;
				slopeCount ++;
				slope = 90 - slope;
				// before adjusting
				if (slope > 90.0)
					slope = (slope - 90) * -1;
				// and printing
				sb2.append(slope);
				sb2.append(")");
			}
			sb2.append("\n");
		}
		if (slopeCount > 0) {
			sb.append("Slope based latitude: ");
			slope = 90.0 - (slopeTotals / slopeCount);
			if (slope > 90.0)
				slope = (slope - 90.0) * -1;
			sb.append(slope);
			sb.append("\n");
		}
		sb.append(sb2.toString());

		canvas.setRenderers(renderers);
		canvas.validate();
		canvas.repaint();
		
		console.setText(sb.toString());
		console.invalidate();
	}
}
