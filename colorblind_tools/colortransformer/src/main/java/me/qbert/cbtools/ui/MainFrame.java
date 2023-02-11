package me.qbert.cbtools.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import me.qbert.cbtools.transformers.ColorMatrixTransformerFactory;
import me.qbert.cbtools.ui.component.Canvas;
import me.qbert.cbtools.ui.dialogs.ColorMatrixDialog;

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

public class MainFrame extends JFrame implements ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -951476669710449935L;

	private Canvas canvas;
	
	private JTextArea console;
	private StringBuffer consoleBuffer;
	
	private ColorMatrixDialog matrixDialog = null;
	
	public MainFrame() {
		super("Colorblind color rotate utility");
		
		consoleBuffer = new StringBuffer();
		
        //Creating the MenuBar and adding components
        JMenuBar mb = new JMenuBar();
        JMenu m1 = new JMenu("File");
        mb.add(m1);
        
        JMenuItem item = new JMenuItem("open");
        m1.add(item);
    	item.addActionListener((ActionEvent event) -> {
		        try {
		        	JFileChooser fileChooser = new JFileChooser();
		        	String dir = System.getProperty("user.dir");
		        	fileChooser.setCurrentDirectory(new File(dir));
		        	int result = fileChooser.showOpenDialog(this);
		        	if (result == JFileChooser.APPROVE_OPTION) {
			            canvas.loadImage(fileChooser.getSelectedFile());
						canvas.invalidate();
				        validate();
				        repaint();
		        	}
	            } catch (Exception e1) {
	            	consoleBuffer.append(e1.getMessage());
	            	console.setText(consoleBuffer.toString());
	            }
		});
        
        m1.addSeparator();
        
        item = new JMenuItem("clipboard");
        m1.add(item);
    	item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Object src = e.getSource();
		        try {
		            canvas.getImageFromClipboard();
					canvas.invalidate();
			        validate();
			        repaint();
	            } catch (Exception e1) {
	            	consoleBuffer.append(e1.getMessage());
	            	console.setText(consoleBuffer.toString());
	            }
			}
		});
        
        m1.addSeparator();
        
        JMenuItem m11 = new JMenuItem("Exit");
        m1.add(m11);
        
        m11.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});
        
        JMenu m2 = new JMenu("Type of Colorblindness");
        mb.add(m2);
        
        List<String> transformNames = ColorMatrixTransformerFactory.getTransformNames();
        for (String name : transformNames) {
        	item = new JMenuItem(name);
        	m2.add(item);
        	item.addActionListener(new ActionListener() {
    			@Override
    			public void actionPerformed(ActionEvent e) {
    				Object src = e.getSource();
    		        try {
    					canvas.setColorTransformer(((JMenuItem)src).getText());
    					canvas.invalidate();
    			        validate();
    			        repaint();
    				} catch (Exception e1) {
    					e1.printStackTrace();
    				}
    			}
    		});
        }
        
        JMenuItem transformWindow = new JMenuItem("Transform");
        transformWindow.addActionListener((ActionEvent event) -> {
			if (matrixDialog == null) {
				matrixDialog = new ColorMatrixDialog(this, "Color transform matrix dialog");
				matrixDialog.setChangeListener(this);
			}
			
			matrixDialog.setVisible(true);
        });

        mb.add(transformWindow);

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(800, 600));
        
        JScrollPane canvasScroller = new JScrollPane(canvas);
        canvasScroller.setPreferredSize(new Dimension(800, 600));
        
        console = new JTextArea();
        console.setMinimumSize(new Dimension(600, 150));
        JScrollPane consoleScroller = new JScrollPane(console);
        consoleScroller.setPreferredSize(new Dimension(800, 150));
        consoleScroller.setMaximumSize(new Dimension(800, 150));

        
        //Adding Components to the frame.
        getContentPane().add(BorderLayout.NORTH, mb);
        getContentPane().add(BorderLayout.CENTER, canvasScroller);
        getContentPane().add(BorderLayout.SOUTH, consoleScroller);

        try {
        canvas.loadImage(new File("test_images/how-good-is-your-color-vision-17.png"));
//        canvas.loadImage(new File("test_images/vertical_test.jpeg"));
        } catch (Exception e) {
        	consoleBuffer.append(e.getMessage());
        	console.setText(consoleBuffer.toString());
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(500, 40));
        setPreferredSize(new Dimension(1200, 900));
        validate();
        repaint();
        pack();       
        setVisible(true);
	}

	@Override
	public void stateChanged(ChangeEvent changeEvent) {
		double [][] matrix = matrixDialog.getTransformMatrix();
		try {
			canvas.setColorRotateTransformer(matrix, 1.0);
			canvas.invalidate();
			canvas.repaint();
		}
		catch (Exception e) {
        	consoleBuffer.append(e.getMessage());
        	console.setText(consoleBuffer.toString());
		}
	}
}
