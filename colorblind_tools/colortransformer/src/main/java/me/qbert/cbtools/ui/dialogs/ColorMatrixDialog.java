package me.qbert.cbtools.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import me.qbert.cbtools.transformers.impl.ColorMatrixTransformer;

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

public class ColorMatrixDialog extends JDialog implements ChangeListener, ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7751382949668064764L;
	
	private JSlider [][] matrixSliders;
	private JTextField [][] matrixValues;
	private boolean ignoreSliderUpdates = false;
	
	private JButton resetMatrix;
	private JButton swapRedGreen;
	private JButton swapRedBlue;
	private JButton swapGreenBlue;
	
	private ChangeListener changeListener = null;

	public ColorMatrixDialog(Frame owner, String title) {
		super(owner, title, false);
		
		matrixSliders = new JSlider[ColorMatrixTransformer.MATRIX_Y][ColorMatrixTransformer.MATRIX_X];
		matrixValues = new JTextField[ColorMatrixTransformer.MATRIX_Y][ColorMatrixTransformer.MATRIX_X];
		
		initUI();
	}
	
	public void setChangeListener(ChangeListener changeListener) {
		this.changeListener = changeListener;
	}
	
	private void initUI() {
		JPanel panel = new JPanel();
		
		BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
		panel.setLayout(layout);
		
		panel.add(createMatrixPanel("Red", 0, 255, 0, 0));
		panel.add(createMatrixPanel("Green", 1, 0, 255, 0));
		panel.add(createMatrixPanel("Blue", 2, 0, 0, 255));
		panel.add(createSwapPanel());
		
		add(panel);

		setPreferredSize(new Dimension(800, 400));
		
		validate();
		repaint();
		pack();
	}
	
	private JPanel createSwapPanel() {
		JPanel panel = new JPanel();
		
		BoxLayout layout = new BoxLayout(panel, BoxLayout.X_AXIS);
		panel.setLayout(layout);
		
		panel.add(resetMatrix = new JButton("Reset"));
		panel.add(swapRedGreen = new JButton("Red <--> Green"));
		panel.add(swapRedBlue = new JButton("Red <--> Blue"));
		panel.add(swapGreenBlue = new JButton("Green <--> Blue"));

		resetMatrix.addActionListener(this);
		swapRedGreen.addActionListener(this);
		swapRedBlue.addActionListener(this);
		swapGreenBlue.addActionListener(this);
		
		return panel;
	}
	
	private JPanel createMatrixPanel(String whichColor, int row, int sliderRed, int sliderGreen, int sliderBlue) {
		JPanel panel = new JPanel();
		
		BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
		panel.setLayout(layout);
		
		panel.add(new JLabel(whichColor));
		panel.add(createMatrixSlider("Red", row, 0, sliderRed));
		panel.add(createMatrixSlider("Green", row, 1, sliderGreen));
		panel.add(createMatrixSlider("Blue", row, 2, sliderBlue));
		
		return panel;
	}
	
	private JPanel createMatrixSlider(String whichColor, int row, int column, int sliderValue) {
		JPanel panel = new JPanel();
		
		BorderLayout layout = new BorderLayout();
		panel.setLayout(layout);
		
		JLabel label = new JLabel(whichColor, JLabel.RIGHT);
		matrixSliders[row][column] = new JSlider(JSlider.HORIZONTAL, 0, 255, 0);
		matrixValues[row][column] = new JTextField(10);
		
		label.setPreferredSize(new Dimension(75, getPreferredSize().height));
		matrixValues[row][column].setPreferredSize(new Dimension(200, getPreferredSize().height));
		
		matrixSliders[row][column].setValue(sliderValue);
		matrixSliders[row][column].addChangeListener(this);
		
		updateInput(row, column);
		
		panel.add(label, layout.WEST);
		panel.add(matrixSliders[row][column], layout.CENTER);
		panel.add(matrixValues[row][column], layout.EAST);
		
		return panel;
	}
	
	private void updateInput(int row, int col) {
		matrixValues[row][col].setText(Integer.toString(matrixSliders[row][col].getValue()));
	}
	
	private void adjustForSlider(int row, int col) {
		int redVal = matrixSliders[row][0].getValue();
		int greenVal = matrixSliders[row][1].getValue();
		int blueVal = matrixSliders[row][2].getValue();
//		double redPercent = redVal / 255.0;
//		double greenPercent = greenVal / 255.0;
//		double bluePercent = blueVal / 255.0;
		
		ignoreSliderUpdates = true;
		
		if (col == 0) {
			int remainder = 255 - redVal;
			double greenPercent = 0.0;
			double bluePercent = 0.0;
			if (greenVal + blueVal > 0.0) {
				greenPercent = (double)greenVal / (double)(greenVal + blueVal);
				bluePercent = (double)blueVal / (double)(greenVal + blueVal);
			}
			matrixSliders[row][1].setValue((int)(remainder * greenPercent));
			matrixSliders[row][2].setValue((int)(remainder * bluePercent));
		}
		else if (col == 1) {
			int remainder = 255 - greenVal;
			double redPercent = 0.0;
			double bluePercent = 0.0;
			if (redVal + blueVal > 0.0) {
				redPercent = (double)redVal / (double)(redVal + blueVal);
				bluePercent = (double)blueVal / (double)(redVal + blueVal);
			}
			matrixSliders[row][0].setValue((int)(remainder * redPercent));
			matrixSliders[row][2].setValue((int)(remainder * bluePercent));
		}
		else if (col == 2) {
			int remainder = 255 - blueVal;
			double redPercent = 0.0;
			double greenPercent = 0.0;
			if (redVal + greenVal > 0.0) {
				redPercent = (double)redVal / (double)(redVal + greenVal);
				greenPercent = (double)greenVal / (double)(redVal + greenVal);
			}
			matrixSliders[row][0].setValue((int)(remainder * redPercent));
			matrixSliders[row][1].setValue((int)(remainder * greenPercent));
		}
		
		updateInput(row, 0);
		updateInput(row, 1);
		updateInput(row, 2);
		
		ignoreSliderUpdates = false;
		
		if (changeListener != null) {
			ChangeEvent ce = new ChangeEvent(this);
			changeListener.stateChanged(ce);
		}
	}
	
	public double [][] getTransformMatrix() {
		double [][] matrix = new double[ColorMatrixTransformer.MATRIX_Y][ColorMatrixTransformer.MATRIX_X];
		
		for (int row = 0;row < matrixSliders.length;row ++) {
			for (int col = 0;col < matrixSliders[row].length;col ++) {
				matrix[row][col] = (double)matrixSliders[row][col].getValue() / 255.0;
			}
		}
		
		return matrix;
	}

	@Override
	public void stateChanged(ChangeEvent changeEvent) {
		if (ignoreSliderUpdates)
			return;
		
		for (int row = 0;row < matrixSliders.length;row ++) {
			for (int col = 0;col < matrixSliders[row].length;col ++) {
				if (changeEvent.getSource() == matrixSliders[row][col]) {
					adjustForSlider(row, col);
				}
			}
		}
	}

	@Override
	public void actionPerformed(ActionEvent actionEvent) {
		Object src = actionEvent.getSource();
		
		ignoreSliderUpdates = true;
		
		if (src == resetMatrix) {
			matrixSliders[0][0].setValue(255);
			adjustForSlider(0, 0);
			matrixSliders[1][1].setValue(255);
			adjustForSlider(1, 1);
			matrixSliders[2][2].setValue(255);
			adjustForSlider(2, 2);
		}
		else if (src == swapRedGreen) {
			matrixSliders[0][1].setValue(255);
			adjustForSlider(0, 1);
			matrixSliders[1][0].setValue(255);
			adjustForSlider(1, 0);
			matrixSliders[2][2].setValue(255);
			adjustForSlider(2, 2);
		}
		else if (src == swapRedBlue) {
			matrixSliders[0][2].setValue(255);
			adjustForSlider(0, 2);
			matrixSliders[1][1].setValue(255);
			adjustForSlider(1, 1);
			matrixSliders[2][0].setValue(255);
			adjustForSlider(2, 0);
		}
		else if (src == swapGreenBlue) {
			matrixSliders[0][0].setValue(255);
			adjustForSlider(0, 0);
			matrixSliders[1][2].setValue(255);
			adjustForSlider(1, 2);
			matrixSliders[2][1].setValue(255);
			adjustForSlider(2, 1);
		}
		
		ignoreSliderUpdates = false;
	}
}
