package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class TimezoneSettingTest {

	@Test
	void systemDefaultResolvesToTheRunningMachinesZone() {
		TimezoneSetting setting = TimezoneSetting.useSystemDefault();

		assertTrue(setting.isUseSystemDefault());
		assertEquals(ZoneId.systemDefault(), setting.resolve());
		assertThrows(IllegalStateException.class, setting::getExplicitZone);
	}

	@Test
	void explicitZoneOverridesTheDefault() {
		// The EXIF-from-someone-else's-timelapse case: the archive isn't in the app operator's own
		// zone, so it must be settable independently.
		ZoneId tokyo = ZoneId.of("Asia/Tokyo");
		TimezoneSetting setting = TimezoneSetting.explicit(tokyo);

		assertFalse(setting.isUseSystemDefault());
		assertEquals(tokyo, setting.getExplicitZone());
		assertEquals(tokyo, setting.resolve());
	}
}
