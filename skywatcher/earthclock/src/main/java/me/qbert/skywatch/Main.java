package me.qbert.skywatch;

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

>>>>>>> 01fd089 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

>>>>>>> af12464 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

>>>>>>> 01fd089 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 01fd089 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
=======
>>>>>>> af12464 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
=======
>>>>>>> 01fd089 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 01fd089 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> af12464 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 01fd089 (new pom version, revamp the earth clock to support multiple projections and lots of nifty new features)
		MainFrame mainFrame = new MainFrame();
	}
}
