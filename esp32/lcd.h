#ifndef LCD_H
#define LCD_H

#include <Arduino.h>
#include <Arduino_GFX_Library.h>

class Esp32RgbDisplay {
  public:
	Esp32RgbDisplay();

	bool init();
	void setBrightness(uint8_t percent);
	void setMirror(bool enabled);
	void flushWindow(uint16_t x1, uint16_t y1, uint16_t x2, uint16_t y2, uint16_t* color);
	void invertDisplay(bool invert);
	Arduino_GFX* gfx();

  private:
	static constexpr uint8_t BACKLIGHT_CHANNEL = 0;

	Arduino_ESP32RGBPanel* _bus;
	Arduino_RPi_DPI_RGBPanel* _gfx;
	bool _initialized = false;
	bool _mirror      = false;
};

extern Esp32RgbDisplay lcd;

#endif
