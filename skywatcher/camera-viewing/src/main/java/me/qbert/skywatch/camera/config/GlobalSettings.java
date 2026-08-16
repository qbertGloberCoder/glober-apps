package me.qbert.skywatch.camera.config;

import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.ColorScheme;

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

// App-wide settings, deliberately separate from any one camera's own persisted .properties file
// (config.CameraConfigStore) - a real user report: "My Location" was previously duplicated per
// camera and reset to 0,0 on every launch, and worldModel (spec §7.1's globe/flat toggle) was
// stored per-camera despite having zero downstream consumers anywhere in the rendering pipeline -
// neither is actually a property of any one camera. Persisted via config.GlobalSettingsStore to
// ~/.camera-viewing/settings.properties by default, alongside (not inside) the per-camera profiles
// under ~/.camera-viewing/cameras/.
//
// myLatitude/myLongitude are nullable as a PAIR (both set or both unset - see setMyLocation(...)/
// clearMyLocation()) - "not configured yet" is a real, valid state (a fresh install, or an operator
// who hasn't set a location yet), matching config.ObserverLocationSetting's own "null until
// explicitly configured" stance elsewhere in this module. This is what
// config.ObserverLocationSetting.resolve(GlobalSettings) (a per-camera "use my locale" setting)
// resolves against - see that method's own comment.
public final class GlobalSettings {
	private Double myLatitude;
	private Double myLongitude;
	private WorldModel worldModel = WorldModel.GLOBE;
	// Item 5 ("Graticule redesign") - confirmed neither of these persisted anywhere before this
	// round: fontSizePixels and the full ColorScheme (including the new reference-line color
	// fields) reset to hardcoded defaults on every launch. Global, not per-camera, since a font
	// size/color scheme is an app-operator preference, not a property of any one camera - same
	// reasoning as myLatitude/myLongitude above.
	private int fontSizePixels = 16;
	private ColorScheme colorScheme = ColorPresets.defaultScheme();

	public boolean hasMyLocation() {
		return myLatitude != null;
	}

	public double getMyLatitude() {
		if (myLatitude == null)
			throw new IllegalStateException("my location has not been configured yet");
		return myLatitude;
	}

	public double getMyLongitude() {
		if (myLongitude == null)
			throw new IllegalStateException("my location has not been configured yet");
		return myLongitude;
	}

	public void setMyLocation(double latitude, double longitude) {
		this.myLatitude = latitude;
		this.myLongitude = longitude;
	}

	public void clearMyLocation() {
		this.myLatitude = null;
		this.myLongitude = null;
	}

	public WorldModel getWorldModel() {
		return worldModel;
	}

	public void setWorldModel(WorldModel worldModel) {
		if (worldModel == null)
			throw new IllegalArgumentException("worldModel must not be null");
		this.worldModel = worldModel;
	}

	public int getFontSizePixels() {
		return fontSizePixels;
	}

	public void setFontSizePixels(int fontSizePixels) {
		if (fontSizePixels <= 0)
			throw new IllegalArgumentException("fontSizePixels must be positive");
		this.fontSizePixels = fontSizePixels;
	}

	public ColorScheme getColorScheme() {
		return colorScheme;
	}

	public void setColorScheme(ColorScheme colorScheme) {
		if (colorScheme == null)
			throw new IllegalArgumentException("colorScheme must not be null");
		this.colorScheme = colorScheme;
	}
}
