package me.qbert.foucault.physics;

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

public class PendulumTest {

	public static void main(String[] args) {
		Pendulum pendulumModel = new Pendulum();
		
    	pendulumModel.setLatitude(0.0);
    	pendulumModel.setPrecessionRate(1.0/30.0);
    	pendulumModel.setPendulumLength(150.0);
    	pendulumModel.setRunMode(true);

		double bobX = pendulumModel.getX();
		double bobY = pendulumModel.getY();
		double bobZ = pendulumModel.getZ();
        System.out.println("??? " + bobX + ", " + bobY + ", " + bobZ);
    	for (int i = 0;i < 5;i ++) {
//    		pendulumModel.stepToNextFrame();
    		pendulumModel.stepOnce();

    		bobX = pendulumModel.getX();
    		bobY = pendulumModel.getY();
    		bobZ = pendulumModel.getZ();
            
            System.out.println("??? " + bobX + ", " + bobY + ", " + bobZ);
    	}
	}

}
