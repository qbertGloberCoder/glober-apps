package me.qbert.mapper;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Timer;

import javax.swing.JFrame;
import javax.swing.JScrollPane;

import me.qbert.mapper.services.MercatorObjects;
import me.qbert.mapper.config.ScenarioConfigLoader;
import me.qbert.mapper.model.AnimationConfiguration;
import me.qbert.mapper.services.AbstractProjectionObject;
import me.qbert.mapper.services.AzimuthalEquidistantSPPObjects;
import me.qbert.mapper.services.AzimuthalEquidistantNPPObjects;
import me.qbert.mapper.services.EquirectilinearObjects;
import me.qbert.mapper.services.GlobeProjectionObject;
import me.qbert.mapper.ui.components.Canvas;
import me.qbert.mapper.utils.AnimationTimer;

public class Main extends JFrame {
	AbstractProjectionObject projectionObject;
	
	private Canvas canvas;
    private Timer timer = null;
	
	private Main(String[] args) throws Exception {
		super("Projection Mapper");
		
		if (args.length != 4)
			throw new Exception("Need exactly 4 parameters: <config.properties> <projection> <observerLatitude> <observerLongitude>");

		Double lat = Double.valueOf(args[2]);
		Double lon = Double.valueOf(args[3]);

		AnimationConfiguration config = ScenarioConfigLoader.load(new File(args[0]));
		config.getObserver().setGeoLocation(lat, lon);

		canvas = new Canvas();

		if (args[1].equals("globe"))
			projectionObject = new GlobeProjectionObject(canvas, config);
		else if (args[1].equals("equirectangular"))
			projectionObject = new EquirectilinearObjects(canvas, config);
		else if (args[1].equals("mercator"))
			projectionObject = new MercatorObjects(canvas, config);
		else if (args[1].equals("ae-north"))
			projectionObject = new AzimuthalEquidistantNPPObjects(canvas, config);
		else if (args[1].equals("ae-south"))
			projectionObject = new AzimuthalEquidistantSPPObjects(canvas, config);
		else
			throw new Exception("projection type unknown");
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().add(BorderLayout.CENTER, new JScrollPane(canvas));

        setPreferredSize(new Dimension(1024, 576));

        validate();
        repaint();
        pack();       
        setVisible(true);
		canvas.repaint();
        
		timer = new Timer();
		AnimationTimer animationTimer = new AnimationTimer(this);
		timer.schedule(animationTimer, 0, 250);
	}

	public void animate(AnimationTimer animationTimer) {
		if (! projectionObject.tickAhead())
			dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));

	}

	public static void main(String[] args) throws Exception {
		Main main = new Main(args);
	}
}
