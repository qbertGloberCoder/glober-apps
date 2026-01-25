package me.qbert;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;

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

public class JavaFX3DBoxExample extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 1. Create a 3D shape (Box)
        Box box = new Box(100, 100, 100);
        
        // 2. Apply a material (color) to the box
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(Color.BLUE);
        box.setMaterial(material);

        // 3. Create a Group to hold the 3D objects
        Group root = new Group(box);

        // 4. Set up the camera
        // The camera is necessary to view 3D objects correctly
        PerspectiveCamera camera = new PerspectiveCamera(false);
        camera.setTranslateX(0);
        camera.setTranslateY(0);
        camera.setTranslateZ(-500); // Move the camera back to see the box
        
        // 5. Create a Scene and add the Group and Camera
        Scene scene = new Scene(root, 600, 400);
        scene.setCamera(camera);
        scene.setFill(Color.LIGHTGRAY); // Set a background color
        
        // 6. Add a rotation animation
        RotateTransition rt = new RotateTransition(Duration.seconds(5), box);
        rt.setAxis(Rotate.Y_AXIS);
        rt.setByAngle(360);
        rt.setInterpolator(Interpolator.LINEAR);
        rt.setCycleCount(Animation.INDEFINITE); // Loop indefinitely
        rt.play();

        // 7. Set the stage and show the scene
        primaryStage.setTitle("JavaFX 3D Example (Box)");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
