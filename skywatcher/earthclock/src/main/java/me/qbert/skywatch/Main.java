package me.qbert.skywatch;

<<<<<<< HEAD
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
import me.qbert.skywatch.ui.MainFrame;

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

public class Main {
	public static void main(String[] args) {
<<<<<<< HEAD
		if (System.getProperty("os.name").startsWith("Linux")) {
			if ((args.length < 1) || (! "nogl".equals(args[0]))) {
		    	System.out.println("setting up opengl");
		    	
		    	System.setProperty("sun.java2d.opengl", "True");
				
		        // set system look and feel
		        try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
						| UnsupportedLookAndFeelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
		MainFrame mainFrame = new MainFrame();
	}
}
