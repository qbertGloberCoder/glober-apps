package me.qbert.cbtools.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Timer;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import me.qbert.cbtools.ui.component.Canvas;

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
	private static final long serialVersionUID = -951476669710449935L;

	private Canvas canvas;
	
	private JTextArea console;
	private StringBuffer consoleBuffer;

	public MainFrame() {
		super("Colorblind color rotate utility");
		
		consoleBuffer = new StringBuffer();
		
        //Creating the MenuBar and adding components
        JMenuBar mb = new JMenuBar();
        JMenu m1 = new JMenu("File");
        mb.add(m1);
        JMenuItem m11 = new JMenuItem("Exit");
        m1.add(m11);
        
        m11.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(600, 600));
        
        console = new JTextArea();
        console.setMinimumSize(new Dimension(600, 600));
        JScrollPane consoleScroller = new JScrollPane(console);
        consoleScroller.setPreferredSize(new Dimension(600, 600));

        //Adding Components to the frame.
        getContentPane().add(BorderLayout.NORTH, mb);
        getContentPane().add(BorderLayout.WEST, new JScrollPane(canvas));
        getContentPane().add(BorderLayout.EAST, consoleScroller);

        try {
        canvas.loadImage(new File("test_images/how-good-is-your-color-vision-17.png"));
        } catch (Exception e) {
        	consoleBuffer.append(e.getMessage());
        	console.setText(consoleBuffer.toString());
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(500, 40));
        validate();
        repaint();
        pack();       
        setVisible(true);
	}
}
