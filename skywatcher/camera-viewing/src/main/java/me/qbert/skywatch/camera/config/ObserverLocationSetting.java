package me.qbert.skywatch.camera.config;

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

// spec §7.1: "Observer location: explicit lat/lon, or 'use my locale'". This is the stored
// per-camera setting, distinct from sw-base's me.qbert.skywatch.astro.ObserverLocation, which is
// the live, listener-bearing runtime object this setting is used to initialize/set at render time.
public final class ObserverLocationSetting {
	private final boolean useSystemLocale;
	private final double latitude;
	private final double longitude;

	private ObserverLocationSetting(boolean useSystemLocale, double latitude, double longitude) {
		this.useSystemLocale = useSystemLocale;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public static ObserverLocationSetting explicit(double latitude, double longitude) {
		return new ObserverLocationSetting(false, latitude, longitude);
	}

	public static ObserverLocationSetting useSystemLocale() {
		return new ObserverLocationSetting(true, 0.0, 0.0);
	}

	public boolean isUseSystemLocale() {
		return useSystemLocale;
	}

	public double getLatitude() {
		if (useSystemLocale)
			throw new IllegalStateException("location is system-locale-derived, not explicit");
		return latitude;
	}

	public double getLongitude() {
		if (useSystemLocale)
			throw new IllegalStateException("location is system-locale-derived, not explicit");
		return longitude;
	}

	// The resolver useSystemLocale() has always implied but never had until GlobalSettings existed
	// (a real gap: batch.CameraImageDispatch used to throw IllegalStateException outright the
	// moment a PTZ camera actually used this mode, with a comment saying so explicitly). An explicit
	// setting resolves to itself; a system-locale one resolves to GlobalSettings' own "my location",
	// throwing a clear, specific error only if THAT hasn't been configured yet either - a real
	// "nothing set anywhere" case, not swallowed silently.
	public ObserverLocationSetting resolve(GlobalSettings globalSettings) {
		if (!useSystemLocale)
			return this;
		if (globalSettings == null || !globalSettings.hasMyLocation())
			throw new IllegalStateException(
					"camera uses \"my location\" but no location has been configured yet - set one in \"My Location\"");
		return ObserverLocationSetting.explicit(globalSettings.getMyLatitude(), globalSettings.getMyLongitude());
	}
}
