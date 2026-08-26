#include "config.h"
#include "lcd.h"

Esp32RgbDisplay lcd;

Esp32RgbDisplay::Esp32RgbDisplay() {
	_bus = new Arduino_ESP32RGBPanel(GFX_NOT_DEFINED, GFX_NOT_DEFINED, GFX_NOT_DEFINED, PIN_RGB_DE, PIN_RGB_VSYNC,
	                                 PIN_RGB_HSYNC, PIN_RGB_PCLK, PIN_RGB_R0, PIN_RGB_R1, PIN_RGB_R2, PIN_RGB_R3,
	                                 PIN_RGB_R4, PIN_RGB_G0, PIN_RGB_G1, PIN_RGB_G2, PIN_RGB_G3, PIN_RGB_G4, PIN_RGB_G5,
	                                 PIN_RGB_B0, PIN_RGB_B1, PIN_RGB_B2, PIN_RGB_B3, PIN_RGB_B4);

	_gfx = new Arduino_RPi_DPI_RGBPanel(_bus, 800, 0, 8, 4, 8, 480, 0, 8, 4, 8, 1, 16000000, true);
}

bool Esp32RgbDisplay::init() {
	ledcSetup(BACKLIGHT_CHANNEL, 1000, 8);
	ledcAttachPin(PIN_BACKLIGHT, BACKLIGHT_CHANNEL);
	ledcWrite(BACKLIGHT_CHANNEL, 0);

	// In the Arduino_GFX release supplied with this display begin() returns void.
	_gfx->begin();
	_gfx->fillScreen(BLACK);

	_initialized = true;
	setBrightness(100);
	return true;
}

void Esp32RgbDisplay::setBrightness(uint8_t percent) {
	percent            = constrain(percent, 0, 100);
	const uint8_t duty = map(percent, 0, 100, 0, 255);
	ledcWrite(BACKLIGHT_CHANNEL, duty);
}

void Esp32RgbDisplay::setMirror(bool enabled) {
	_mirror = enabled;
}

void Esp32RgbDisplay::flushWindow(uint16_t x1, uint16_t y1, uint16_t x2, uint16_t y2, uint16_t* color) {
	if (!_initialized || color == nullptr) {
		return;
	}

	const uint16_t width  = x2 - x1 + 1;
	const uint16_t height = y2 - y1 + 1;

	// Normal output
	if (!_mirror) {
		_gfx->draw16bitRGBBitmap(x1, y1, color, width, height);
		return;
	}

	// Mirrored output for HUD use
	static uint16_t mirrorLine[SCREEN_WIDTH];

	const int16_t mirroredX = SCREEN_WIDTH - 1 - x2;

	for (uint16_t row = 0; row < height; row++) {
		const uint16_t* src = color + (row * width);

		for (uint16_t col = 0; col < width; col++) {
			mirrorLine[col] = src[width - 1 - col];
		}

		_gfx->draw16bitRGBBitmap(mirroredX, y1 + row, mirrorLine, width, 1);
	}
}

void Esp32RgbDisplay::invertDisplay(bool invert) {
	// An RGB panel has no ST7789-style hardware inversion command.
	// Retained as a no-op so the existing theme controller still compiles.
	(void)invert;
}

Arduino_GFX* Esp32RgbDisplay::gfx() {
	return _gfx;
}
