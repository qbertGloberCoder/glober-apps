package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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

class ObserverLocationSettingTest {

	@Test
	void explicitSettingResolvesToItselfRegardlessOfGlobalSettings() {
		ObserverLocationSetting explicit = ObserverLocationSetting.explicit(10.0, 20.0);

		ObserverLocationSetting resolved = explicit.resolve(null);

		assertEquals(explicit, resolved);
		assertEquals(10.0, resolved.getLatitude(), 0.0001);
		assertEquals(20.0, resolved.getLongitude(), 0.0001);
	}

	@Test
	void systemLocaleSettingResolvesFromGlobalSettingsMyLocation() {
		GlobalSettings globalSettings = new GlobalSettings();
		globalSettings.setMyLocation(45.5, -75.25);

		ObserverLocationSetting resolved = ObserverLocationSetting.useSystemLocale().resolve(globalSettings);

		assertEquals(45.5, resolved.getLatitude(), 0.0001);
		assertEquals(-75.25, resolved.getLongitude(), 0.0001);
	}

	@Test
	void systemLocaleSettingThrowsAClearErrorWhenNeitherIsConfigured() {
		ObserverLocationSetting useSystemLocale = ObserverLocationSetting.useSystemLocale();

		assertThrows(IllegalStateException.class, () -> useSystemLocale.resolve(null));
		assertThrows(IllegalStateException.class, () -> useSystemLocale.resolve(new GlobalSettings()));
	}
}
